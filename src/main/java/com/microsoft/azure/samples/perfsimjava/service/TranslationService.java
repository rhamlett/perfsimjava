package com.microsoft.azure.samples.perfsimjava.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microsoft.azure.samples.perfsimjava.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Translates UI strings and HTML documents from English to a target language
 * using Azure Cognitive Services Translator Text API.
 */
@Service
public class TranslationService {

    private static final Logger logger = LoggerFactory.getLogger(TranslationService.class);

    private static final int MAX_BATCH_SIZE = 100;
    private static final int MAX_BATCH_CHARS = 49_000;

    private static final Pattern PLACEHOLDER_REGEX = Pattern.compile("\\{[a-zA-Z_][a-zA-Z0-9_]*\\}");
    private static final Pattern NOTRANSLATE_SPAN_REGEX = Pattern.compile(
            "<span\\s+class\\s*=\\s*(?:\"|&quot;|')notranslate(?:\"|&quot;|')>(.*?)</span>",
            Pattern.DOTALL);
    private static final Pattern HTML_TAG_REGEX = Pattern.compile("(<[^>]+>)");
    private static final Pattern NO_TRANSLATE_ELEMENT_OPEN_REGEX = Pattern.compile(
            "<(code|pre|script|style|svg)[\\s>]", Pattern.CASE_INSENSITIVE);

    private final AppConfig appConfig;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public TranslationService(AppConfig appConfig) {
        this.appConfig = appConfig;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * Pauses the current thread for rate-limiting between API batches.
     */
    private void interBatchDelay(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }

    /**
     * Ensures a translated locale file exists for the specified language.
     */
    public boolean ensureTranslation(String targetLanguage, Path localesPath) {
        if (targetLanguage == null || targetLanguage.isBlank() || targetLanguage.equalsIgnoreCase("en")) {
            return true;
        }

        Path enFilePath = localesPath.resolve("en.json");
        Path targetFilePath = localesPath.resolve(targetLanguage + ".json");

        if (!Files.exists(enFilePath)) {
            logger.error("English source file not found: {}", enFilePath);
            return false;
        }

        try {
            String enContent = Files.readString(enFilePath, StandardCharsets.UTF_8);
            String sourceHash = computeHash(enContent);

            // Check cache
            if (Files.exists(targetFilePath)) {
                try {
                    String existingContent = Files.readString(targetFilePath, StandardCharsets.UTF_8);
                    JsonNode existingDoc = objectMapper.readTree(existingContent);
                    JsonNode meta = existingDoc.get("_meta");
                    if (meta != null && meta.has("source_hash")) {
                        String existingHash = meta.get("source_hash").asText();
                        if (sourceHash.equals(existingHash)) {
                            logger.info("Translation for {} is up to date (hash: {})",
                                    targetLanguage, sourceHash.substring(0, 8));
                            return true;
                        }
                    }
                    logger.info("Translation for {} exists but source has changed, re-translating", targetLanguage);
                } catch (IOException | IllegalArgumentException e) {
                    logger.warn("Existing translation file for {} is invalid, re-translating", targetLanguage);
                }
            }

            String translatorKey = appConfig.getTranslatorApiKey();
            if (translatorKey == null || translatorKey.isBlank()) {
                logger.warn("UI_LANGUAGE is set to '{}' but TranslatorApiKey is not configured. "
                        + "Set TRANSLATOR_API_KEY environment variable to enable auto-translation.", targetLanguage);
                return false;
            }

            // Parse English strings (skip _meta)
            JsonNode enDoc = objectMapper.readTree(enContent);
            Map<String, String> sourceStrings = new LinkedHashMap<>();
            enDoc.fields().forEachRemaining(entry -> {
                if (!"_meta".equals(entry.getKey()) && entry.getValue().isTextual()) {
                    sourceStrings.put(entry.getKey(), entry.getValue().asText());
                }
            });

            if (sourceStrings.isEmpty()) {
                logger.warn("No translatable strings found in en.json");
                return false;
            }

            // Load no-translate terms
            List<String> noTranslateTerms = loadNoTranslateTerms(localesPath);

            logger.info("Translating {} strings to {} ({} protected terms)...",
                    sourceStrings.size(), targetLanguage, noTranslateTerms.size());

            // Translate in batches
            Map<String, String> translatedStrings = new LinkedHashMap<>();
            List<String> keys = new ArrayList<>(sourceStrings.keySet());

            for (int i = 0; i < keys.size(); ) {
                List<String> batchKeys = new ArrayList<>();
                List<String> batchTexts = new ArrayList<>();
                int batchCharCount = 0;

                while (i < keys.size() && batchKeys.size() < MAX_BATCH_SIZE) {
                    String wrapped = wrapNoTranslateTerms(sourceStrings.get(keys.get(i)), noTranslateTerms);
                    if (batchCharCount + wrapped.length() > MAX_BATCH_CHARS && !batchKeys.isEmpty()) {
                        break;
                    }
                    batchKeys.add(keys.get(i));
                    batchTexts.add(wrapped);
                    batchCharCount += wrapped.length();
                    i++;
                }

                List<String> translations = translateBatch(batchTexts, targetLanguage);
                if (translations == null) {
                    logger.error("Translation API call failed for batch starting at index {}", i - batchKeys.size());
                    return false;
                }

                for (int j = 0; j < batchKeys.size(); j++) {
                    translatedStrings.put(batchKeys.get(j), stripNoTranslateTags(translations.get(j)));
                }

                // Inter-batch delay
                if (i < keys.size()) {
                    interBatchDelay(2000);
                }
            }

            // Build output JSON
            ObjectNode output = objectMapper.createObjectNode();
            ObjectNode metaNode = objectMapper.createObjectNode();
            metaNode.put("source_hash", sourceHash);
            metaNode.put("source_lang", "en");
            metaNode.put("target_lang", targetLanguage);
            metaNode.put("generated", Instant.now().toString());
            metaNode.put("generator", "Azure Cognitive Services Translator");
            output.set("_meta", metaNode);

            translatedStrings.forEach(output::put);

            String outputJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(output);
            Files.writeString(targetFilePath, outputJson, StandardCharsets.UTF_8);

            logger.info("Translation complete: {} strings written to {}", translatedStrings.size(), targetFilePath);
            return true;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Translation interrupted for {}", targetLanguage);
            return false;
        } catch (IOException e) {
            logger.error("Translation failed for {}: {}", targetLanguage, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Ensures a translated HTML document exists for the specified language.
     */
    public boolean ensureDocumentTranslation(Path sourceHtmlPath, String targetLanguage) {
        if (targetLanguage == null || targetLanguage.isBlank() || targetLanguage.equalsIgnoreCase("en")) {
            return true;
        }

        if (!Files.exists(sourceHtmlPath)) {
            logger.error("HTML source file not found: {}", sourceHtmlPath);
            return false;
        }

        try {
            String sourceContent = Files.readString(sourceHtmlPath, StandardCharsets.UTF_8);
            String sourceHash = computeHash(sourceContent);
            Path targetPath = getTranslatedHtmlPath(sourceHtmlPath, targetLanguage);
            String sourceFileName = sourceHtmlPath.getFileName().toString();

            // Check cache
            if (Files.exists(targetPath)) {
                List<String> lines = Files.readAllLines(targetPath, StandardCharsets.UTF_8);
                if (!lines.isEmpty() && lines.get(0).contains("source_hash:" + sourceHash)) {
                    logger.info("Document translation for {} ({}) is up to date (hash: {})",
                            sourceFileName, targetLanguage, sourceHash.substring(0, 8));
                    return true;
                }
                logger.info("Document translation for {} ({}) exists but source changed, re-translating",
                        sourceFileName, targetLanguage);
            }

            String translatorKey = appConfig.getTranslatorApiKey();
            if (translatorKey == null || translatorKey.isBlank()) {
                logger.warn("Cannot translate {} to '{}' — TranslatorApiKey is not configured.",
                        sourceFileName, targetLanguage);
                return false;
            }

            // Load no-translate terms
            Path localesPath = sourceHtmlPath.getParent().resolve("locales");
            if (!Files.exists(localesPath)) {
                // locales might be sibling of static root
                localesPath = sourceHtmlPath.getParent().resolve("locales");
            }
            List<String> noTranslateTerms = loadNoTranslateTerms(localesPath);

            // Extract translatable segments
            List<HtmlSegment> segments = extractTranslatableSegments(sourceContent);
            List<HtmlSegment> translatableSegments = segments.stream()
                    .filter(s -> s.isTranslatable && s.text != null && !s.text.isBlank())
                    .toList();

            if (translatableSegments.isEmpty()) {
                logger.warn("No translatable text found in {}", sourceFileName);
                return false;
            }

            logger.info("Translating document {} to {}: {} text segments...",
                    sourceFileName, targetLanguage, translatableSegments.size());

            // Translate in character-aware batches
            int batchIndex = 0;
            int i = 0;
            while (i < translatableSegments.size()) {
                List<HtmlSegment> batch = new ArrayList<>();
                List<String> batchTexts = new ArrayList<>();
                int batchCharCount = 0;

                while (i < translatableSegments.size() && batch.size() < MAX_BATCH_SIZE) {
                    String wrapped = wrapNoTranslateTerms(translatableSegments.get(i).text, noTranslateTerms);
                    if (batchCharCount + wrapped.length() > MAX_BATCH_CHARS && !batch.isEmpty()) {
                        break;
                    }
                    batch.add(translatableSegments.get(i));
                    batchTexts.add(wrapped);
                    batchCharCount += wrapped.length();
                    i++;
                }

                List<String> translations = translateBatch(batchTexts, targetLanguage);
                if (translations == null) {
                    logger.error("Document translation API call failed for {} at batch {}", sourceFileName, batchIndex);
                    return false;
                }

                for (int j = 0; j < batch.size(); j++) {
                    batch.get(j).translatedText = stripNoTranslateTags(translations.get(j));
                }

                batchIndex++;

                // Inter-batch delay
                if (i < translatableSegments.size()) {
                    interBatchDelay(2000);
                }
            }

            // Reassemble translated HTML
            StringBuilder sb = new StringBuilder();
            sb.append("<!-- source_hash:").append(sourceHash)
                    .append(" lang:").append(targetLanguage)
                    .append(" generated:").append(Instant.now().toString())
                    .append(" -->\n");
            for (HtmlSegment segment : segments) {
                if (segment.isTranslatable && segment.translatedText != null) {
                    sb.append(segment.translatedText);
                } else {
                    sb.append(segment.text);
                }
            }

            Files.writeString(targetPath, sb.toString(), StandardCharsets.UTF_8);

            logger.info("Document translation complete: {} → {} ({} segments)",
                    sourceFileName, targetPath.getFileName(), translatableSegments.size());
            return true;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Document translation interrupted for {}", sourceHtmlPath.getFileName());
            return false;
        } catch (IOException e) {
            logger.error("Document translation failed for {}: {}", sourceHtmlPath.getFileName(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Calls the Azure Translator API to translate a batch of strings.
     * Retries up to 4 times on 429 (rate limit) with exponential backoff.
     */
    private List<String> translateBatch(List<String> texts, String targetLanguage)
            throws IOException, InterruptedException {
        String apiKey = appConfig.getTranslatorApiKey();
        String endpoint = appConfig.getTranslatorEndpoint();
        String region = appConfig.getTranslatorRegion();

        // Build request body
        List<Map<String, String>> requestBody = new ArrayList<>();
        for (String text : texts) {
            requestBody.add(Map.of("Text", text));
        }
        String requestJson = objectMapper.writeValueAsString(requestBody);

        String requestUrl = endpoint + "/translate?api-version=3.0&from=en&to="
                + URLEncoder.encode(targetLanguage, StandardCharsets.UTF_8) + "&textType=html";

        int[] retryDelays = {5, 15, 30, 60};

        for (int attempt = 0; attempt <= retryDelays.length; attempt++) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(requestUrl))
                    .header("Content-Type", "application/json")
                    .header("Ocp-Apim-Subscription-Key", apiKey)
                    .header("Ocp-Apim-Subscription-Region", region)
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode responseDoc = objectMapper.readTree(response.body());
                List<String> results = new ArrayList<>();
                for (JsonNode item : responseDoc) {
                    JsonNode translations = item.get("translations");
                    results.add(translations.get(0).get("text").asText());
                }
                return results;
            }

            if (response.statusCode() == 429 && attempt < retryDelays.length) {
                int delay = retryDelays[attempt];

                // Check Retry-After header
                Optional<String> retryAfter = response.headers().firstValue("Retry-After");
                if (retryAfter.isPresent()) {
                    try {
                        delay = Integer.parseInt(retryAfter.get());
                    } catch (NumberFormatException ignored) {
                    }
                }

                logger.warn("Translator API rate limited (attempt {}/{}). Retrying in {}s...",
                        attempt + 1, retryDelays.length + 1, delay);
                interBatchDelay(delay * 1000L);
                continue;
            }

            logger.error("Azure Translator API returned {}: {}", response.statusCode(), response.body());
            return null;
        }

        return null;
    }

    /**
     * Loads the no-translate terms list from no-translate.json, sorted longest-first.
     */
    private List<String> loadNoTranslateTerms(Path localesPath) {
        Path noTranslatePath = localesPath.resolve("no-translate.json");
        if (!Files.exists(noTranslatePath)) {
            return Collections.emptyList();
        }

        try {
            String content = Files.readString(noTranslatePath, StandardCharsets.UTF_8);
            JsonNode doc = objectMapper.readTree(content);
            JsonNode termsArray = doc.get("terms");
            if (termsArray != null && termsArray.isArray()) {
                List<String> terms = new ArrayList<>();
                for (JsonNode term : termsArray) {
                    String t = term.asText();
                    if (t != null && !t.isEmpty()) {
                        terms.add(t);
                    }
                }
                // Sort longest first to avoid partial matches
                terms.sort((a, b) -> Integer.compare(b.length(), a.length()));
                return terms;
            }
        } catch (IOException e) {
            logger.warn("Failed to load no-translate.json: {}", e.getMessage());
        }

        return Collections.emptyList();
    }

    /**
     * Wraps {placeholder} tokens and no-translate terms in notranslate spans.
     */
    private String wrapNoTranslateTerms(String text, List<String> terms) {
        // Wrap {placeholder} tokens first
        text = PLACEHOLDER_REGEX.matcher(text).replaceAll("<span class=\"notranslate\">$0</span>");

        if (terms.isEmpty()) return text;

        for (String term : terms) {
            String pattern = "(?<![a-zA-Z])" + Pattern.quote(term) + "(?![a-zA-Z])";
            text = Pattern.compile(pattern).matcher(text)
                    .replaceAll("<span class=\"notranslate\">" + Matcher.quoteReplacement(term) + "</span>");
        }

        return text;
    }

    /**
     * Strips notranslate span tags from translated text.
     * Handles double quotes, single quotes, and HTML entities.
     */
    private String stripNoTranslateTags(String text) {
        return NOTRANSLATE_SPAN_REGEX.matcher(text).replaceAll("$1");
    }

    /**
     * Computes a SHA256 hash of the input string (first 16 hex chars).
     */
    private String computeHash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.substring(0, 16);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Gets the path for a translated HTML document.
     * e.g., docs.html with lang "es" → docs.es.html
     */
    private Path getTranslatedHtmlPath(Path sourceHtmlPath, String targetLanguage) {
        String fileName = sourceHtmlPath.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        String nameWithoutExt = fileName.substring(0, dotIndex);
        String ext = fileName.substring(dotIndex);
        return sourceHtmlPath.getParent().resolve(nameWithoutExt + "." + targetLanguage + ext);
    }

    /**
     * Splits an HTML document into translatable text segments and non-translatable markup.
     */
    private List<HtmlSegment> extractTranslatableSegments(String html) {
        List<HtmlSegment> segments = new ArrayList<>();
        String[] parts = HTML_TAG_REGEX.split(html, -1);
        
        // Also find all tags to interleave
        java.util.regex.Matcher tagMatcher = HTML_TAG_REGEX.matcher(html);
        List<String> tags = new ArrayList<>();
        while (tagMatcher.find()) {
            tags.add(tagMatcher.group(1));
        }

        Map<String, Integer> noTranslateDepth = new HashMap<>();
        boolean insideNoTranslate = false;
        int tagIndex = 0;

        for (String part : parts) {
            if (part == null || part.isEmpty()) {
                // Insert tag if available
                if (tagIndex < tags.size()) {
                    String tag = tags.get(tagIndex++);
                    segments.add(new HtmlSegment(tag, false));
                    updateNoTranslateState(tag, noTranslateDepth);
                    insideNoTranslate = noTranslateDepth.values().stream().anyMatch(v -> v > 0);
                }
                continue;
            }

            // This is a text segment
            boolean shouldTranslate = !insideNoTranslate && !part.isBlank();
            segments.add(new HtmlSegment(part, shouldTranslate));

            // Insert the tag that follows this text part
            if (tagIndex < tags.size()) {
                String tag = tags.get(tagIndex++);
                segments.add(new HtmlSegment(tag, false));
                updateNoTranslateState(tag, noTranslateDepth);
                insideNoTranslate = noTranslateDepth.values().stream().anyMatch(v -> v > 0);
            }
        }

        return segments;
    }

    private void updateNoTranslateState(String tag, Map<String, Integer> noTranslateDepth) {
        java.util.regex.Matcher openMatch = NO_TRANSLATE_ELEMENT_OPEN_REGEX.matcher(tag);
        if (openMatch.find()) {
            String tagName = openMatch.group(1).toLowerCase();
            noTranslateDepth.merge(tagName, 1, Integer::sum);
        } else if (tag.startsWith("</")) {
            String closingTag = tag.substring(2).replaceAll("[>\\s]", "").toLowerCase();
            if (noTranslateDepth.containsKey(closingTag)) {
                int newVal = noTranslateDepth.get(closingTag) - 1;
                if (newVal <= 0) {
                    noTranslateDepth.remove(closingTag);
                } else {
                    noTranslateDepth.put(closingTag, newVal);
                }
            }
        }
    }

    /**
     * Represents a segment of an HTML document.
     */
    private static class HtmlSegment {
        String text;
        boolean isTranslatable;
        String translatedText;

        HtmlSegment(String text, boolean isTranslatable) {
            this.text = text;
            this.isTranslatable = isTranslatable;
        }
    }
}

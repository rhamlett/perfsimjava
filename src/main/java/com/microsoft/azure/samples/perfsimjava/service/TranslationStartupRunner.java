package com.microsoft.azure.samples.perfsimjava.service;

import com.microsoft.azure.samples.perfsimjava.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Runs at application startup to translate UI strings and HTML documents
 * to the configured target language. Runs with highest priority (lowest order)
 * so translations are ready before the app starts serving requests.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TranslationStartupRunner implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(TranslationStartupRunner.class);

    /**
     * HTML documents that should be translated (relative to static root).
     */
    private static final List<String> TRANSLATABLE_DOCUMENTS = List.of(
            "index.html",
            "docs.html",
            "azure-diagnostics.html",
            "azure-load-testing.html",
            "azure-deployment.html"
    );

    private final AppConfig appConfig;
    private final TranslationService translationService;

    public TranslationStartupRunner(AppConfig appConfig, TranslationService translationService) {
        this.appConfig = appConfig;
        this.translationService = translationService;
    }

    @Override
    public void run(ApplicationArguments args) {
        String targetLanguage = appConfig.getUiLanguage();
        if (targetLanguage == null || targetLanguage.isBlank() || targetLanguage.equalsIgnoreCase("en")) {
            logger.info("[i18n] UI language is English — skipping translation");
            return;
        }

        logger.info("[i18n] Starting translation to '{}' at startup...", targetLanguage);

        Path staticRoot = resolveStaticRoot();
        if (staticRoot == null) {
            logger.error("[i18n] Could not locate static resources directory");
            return;
        }

        // Step 1: Translate locale strings (en.json → {lang}.json)
        Path localesPath = staticRoot.resolve("locales");
        boolean stringsOk = translationService.ensureTranslation(targetLanguage, localesPath);
        if (stringsOk) {
            logger.info("[i18n] ✓ Locale strings translated successfully");
        } else {
            logger.warn("[i18n] ✗ Locale strings translation failed or skipped");
        }

        // Step 2: Translate HTML documents
        int successCount = 0;
        int failCount = 0;
        for (String docName : TRANSLATABLE_DOCUMENTS) {
            Path docPath = staticRoot.resolve(docName);
            if (!Files.exists(docPath)) {
                logger.warn("[i18n] Document not found, skipping: {}", docName);
                continue;
            }

            boolean docOk = translationService.ensureDocumentTranslation(docPath, targetLanguage);
            if (docOk) {
                successCount++;
            } else {
                failCount++;
            }
        }

        logger.info("[i18n] Translation startup complete: {} documents translated, {} failed",
                successCount, failCount);
    }

    /**
     * Resolves the static resources root directory.
     * Works both in development (file system) and packaged jar (extracts to temp if needed).
     */
    private Path resolveStaticRoot() {
        // Try development path first
        Path devPath = Path.of("src/main/resources/static");
        if (Files.exists(devPath)) {
            return devPath;
        }

        // Try classpath resource location (packaged jar)
        try {
            var resource = getClass().getClassLoader().getResource("static/");
            if (resource != null) {
                return Path.of(resource.toURI());
            }
        } catch (java.net.URISyntaxException | SecurityException e) {
            logger.debug("[i18n] Could not resolve classpath static root: {}", e.getMessage());
        }

        // Try current working directory
        Path cwdPath = Path.of("static");
        if (Files.exists(cwdPath)) {
            return cwdPath;
        }

        return null;
    }
}

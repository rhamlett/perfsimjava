package com.microsoft.azure.samples.perfsimjava.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Servlet filter that rewrites requests for .html files to their translated
 * variants (e.g. docs.html → docs.es.html) when a non-English UI language
 * is configured and the translated file exists.
 *
 * Resolution order:
 * 1. Filesystem source directory (src/main/resources/static/) — for local dev
 *    where translated files are written at startup and can be committed to git.
 * 2. Classpath (static/) — for jar deployment where committed translated files
 *    are already packaged inside the jar.
 */
@Configuration
public class TranslatedHtmlFilterConfig {

    private static final Logger logger = LoggerFactory.getLogger(TranslatedHtmlFilterConfig.class);

    @Bean
    public FilterRegistrationBean<TranslatedHtmlFilter> translatedHtmlFilter(AppConfig appConfig) {
        FilterRegistrationBean<TranslatedHtmlFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TranslatedHtmlFilter(appConfig));
        registration.addUrlPatterns("*.html");
        registration.setOrder(1);
        registration.setName("translatedHtmlFilter");
        return registration;
    }

    public static class TranslatedHtmlFilter implements Filter {

        private final AppConfig appConfig;

        public TranslatedHtmlFilter(AppConfig appConfig) {
            this.appConfig = appConfig;
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {

            String language = appConfig.getUiLanguage();

            // Skip if English or not HTTP
            if (language == null || language.isBlank() || language.equalsIgnoreCase("en")) {
                chain.doFilter(request, response);
                return;
            }

            if (!(request instanceof HttpServletRequest httpRequest)) {
                chain.doFilter(request, response);
                return;
            }

            String requestUri = httpRequest.getRequestURI();

            // Only intercept .html requests (skip already-translated filenames)
            if (!requestUri.endsWith(".html") || requestUri.contains("." + language + ".html")) {
                chain.doFilter(request, response);
                return;
            }

            // Build the translated filename: /docs.html → docs.es.html
            String relativePath = requestUri.startsWith("/") ? requestUri.substring(1) : requestUri;
            int dotIndex = relativePath.lastIndexOf('.');
            String translatedRelative = relativePath.substring(0, dotIndex) + "." + language + ".html";

            // Check 1: Filesystem source directory (local development)
            // Files written by TranslationStartupRunner live here and can be committed to git.
            Path sourceFilePath = Path.of("src/main/resources/static", translatedRelative);
            if (Files.exists(sourceFilePath)) {
                logger.debug("Serving translated file from source: {}", sourceFilePath);
                serveFile(sourceFilePath, (HttpServletResponse) response);
                return;
            }

            // Check 2: Classpath (packaged jar with committed translations)
            ClassPathResource classpathResource = new ClassPathResource("static/" + translatedRelative);
            if (classpathResource.exists()) {
                logger.debug("Serving translated file from classpath: {}", translatedRelative);
                ((HttpServletResponse) response).setContentType("text/html;charset=UTF-8");
                try (InputStream is = classpathResource.getInputStream()) {
                    response.getOutputStream().write(is.readAllBytes());
                }
                return;
            }

            // No translated version found — serve original English file
            chain.doFilter(request, response);
        }

        private void serveFile(Path filePath, HttpServletResponse response) throws IOException {
            response.setContentType("text/html;charset=UTF-8");
            byte[] content = Files.readAllBytes(filePath);
            response.getOutputStream().write(content);
        }
    }
}

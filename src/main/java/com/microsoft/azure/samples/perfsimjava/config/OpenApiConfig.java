package com.microsoft.azure.samples.perfsimjava.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Performance Problem Simulator API")
                        .version("1.0.0")
                        .description("Educational tool for Azure App Service diagnostics training. " +
                                "This API allows you to simulate various performance problems including " +
                                "CPU stress, memory pressure, thread starvation, failed requests, and more.")
                        .contact(new Contact()
                                .name("Microsoft Azure Samples")
                                .url("https://github.com/Azure-Samples"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")));
    }
}

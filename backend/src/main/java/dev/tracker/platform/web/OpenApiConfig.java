package dev.tracker.platform.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    @Bean
    OpenAPI trackerOpenApi(ObjectProvider<BuildProperties> build) {
        BuildProperties props = build.getIfAvailable();
        return new OpenAPI().info(new Info()
                .title("Tracker API")
                .version(props != null ? props.getVersion() : "dev")
                .description("REST API v1. Errors use RFC 9457 problem details with a requestId property."));
    }
}

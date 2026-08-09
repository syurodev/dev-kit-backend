package com.synx.devkit.bootstrap.configuration;

import org.springframework.boot.jackson.autoconfigure.JsonFactoryBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.StreamReadFeature;

/** Rejects duplicate JSON keys instead of silently accepting the last value. */
@Configuration
public class JacksonConfiguration {
    @Bean
    JsonFactoryBuilderCustomizer strictJsonKeys() {
        return builder -> builder.enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION);
    }
}

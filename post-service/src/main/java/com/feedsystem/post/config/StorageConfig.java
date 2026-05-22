package com.feedsystem.post.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves uploaded files at GET /files/{filename}
 * so image URLs returned by the API are directly accessible in the browser.
 */
@Configuration
public class StorageConfig implements WebMvcConfigurer {

    @Value("${storage.upload-dir}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/files/**")
            .addResourceLocations("file:" + uploadDir + "/");
    }
}

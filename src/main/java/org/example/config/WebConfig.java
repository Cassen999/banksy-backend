package org.example.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                // Allows requests from your React dev server (Vite uses 5173, CRA uses 3000)
                .allowedOrigins("http://localhost:3000", "http://localhost:5173")
                .allowedMethods("GET");
    }
}

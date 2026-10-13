package com.smartline.smartos.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Single authority for CORS, consumed by Spring Security's {@code CorsFilter}.
 * A {@code WebMvcConfigurer} mapping is deliberately NOT also registered:
 * Security's filter answers preflights before authentication, and having both
 * would emit duplicate {@code Access-Control-Allow-Origin} headers, which
 * browsers reject.
 */
@Configuration
public class WebCorsConfig {

  @Value("${app.cors.allowed-origins:http://localhost:3000}")
  private String[] allowedOrigins;

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    // Explicit origins only: allowCredentials(true) forbids "*".
    config.setAllowedOrigins(Arrays.asList(allowedOrigins));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setAllowCredentials(true);
    config.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", config);
    return source;
  }
}

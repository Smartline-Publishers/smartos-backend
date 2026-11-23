package com.smartline.smartos.config;

import java.net.URI;
import java.net.URISyntaxException;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

/**
 * Allows configuring the datasource with a single Neon/Heroku-style
 * {@code DATABASE_URL} (e.g. {@code postgresql://user:pass@host/db?sslmode=require})
 * instead of separate {@code DB_URL}/{@code DB_USER}/{@code DB_PASSWORD} values.
 *
 * When {@code DATABASE_URL} is present and {@code DB_URL} is not explicitly set,
 * it is converted to a JDBC URL and the credentials are extracted, so the
 * application can connect using "just the URL".
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

  @Override
  public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
    MutablePropertySources sources = environment.getPropertySources();
    String databaseUrl = sources.contains("systemEnvironment")
        ? (String) ((org.springframework.core.env.PropertySource<?>) sources.get("systemEnvironment"))
            .getProperty("DATABASE_URL")
        : null;
    // Also check system properties (for local runs).
    if (databaseUrl == null || databaseUrl.isBlank()) {
      databaseUrl = System.getProperty("DATABASE_URL");
    }
    if (databaseUrl == null || databaseUrl.isBlank()) {
      return;
    }
    // Don't override an explicitly provided DB_URL (check raw env/property,
    // not the YAML default which is always non-blank).
    String explicitDbUrl = System.getenv("DB_URL");
    if (explicitDbUrl == null || explicitDbUrl.isBlank()) {
      explicitDbUrl = System.getProperty("DB_URL");
    }
    if (explicitDbUrl != null && !explicitDbUrl.isBlank()) {
      return;
    }

    try {
      URI uri = new URI(databaseUrl.trim());
      String scheme = uri.getScheme();
      if (scheme == null || (!scheme.equals("postgresql") && !scheme.equals("postgres"))) {
        return;
      }
      String host = uri.getHost();
      int port = uri.getPort() == -1 ? 5432 : uri.getPort();
      String path = uri.getPath();
      if (path == null || path.isBlank() || path.equals("/")) {
        path = "/smartos";
      }
      String query = uri.getQuery();
      if (query == null || !query.contains("sslmode")) {
        query = (query == null || query.isBlank()) ? "sslmode=require" : query + "&sslmode=require";
      }

      String userInfo = uri.getUserInfo();
      String user = null;
      String password = null;
      if (userInfo != null && !userInfo.isBlank()) {
        String[] parts = userInfo.split(":", 2);
        user = parts[0];
        if (parts.length > 1) {
          password = parts[1];
        }
      }

      String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + path + "?" + query;

      java.util.Map<String, Object> overrides = new java.util.HashMap<>();
      overrides.put("DB_URL", jdbcUrl);
      if (user != null && !user.isBlank()) {
        overrides.put("DB_USER", user);
      }
      if (password != null && !password.isBlank()) {
        overrides.put("DB_PASSWORD", password);
      }
      sources.addFirst(new MapPropertySource("databaseUrlOverrides", overrides));
    } catch (URISyntaxException ignored) {
      // Leave configuration untouched if the URL can't be parsed.
    }
  }
}
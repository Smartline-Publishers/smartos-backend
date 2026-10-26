package com.smartline.smartos.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartline.smartos.common.ApiResponse;
import com.smartline.smartos.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final ObjectMapper objectMapper;
  private final CorsConfigurationSource corsConfigurationSource;

  public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, ObjectMapper objectMapper,
      CorsConfigurationSource corsConfigurationSource) {
    this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    this.objectMapper = objectMapper;
    this.corsConfigurationSource = corsConfigurationSource;
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .csrf(AbstractHttpConfigurer::disable)
        // CorsFilter answers preflights here, before authentication: without
        // this, an OPTIONS request without credentials falls through to the
        // 401 entry point below, which carries no CORS headers, and browsers
        // block the real request.
        .cors(cors -> cors.configurationSource(corsConfigurationSource))
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        // Filter-chain rejections happen before controllers, so @RestControllerAdvice
        // cannot map them: return the same ApiResponse envelope for 401/403 here.
        .exceptionHandling(e -> e
            .authenticationEntryPoint((req, res, ex) -> writeError(res,
                HttpServletResponse.SC_UNAUTHORIZED,
                ErrorCode.UNAUTHORIZED.getCode(), "Authentication required"))
            .accessDeniedHandler((req, res, ex) -> writeError(res,
                HttpServletResponse.SC_FORBIDDEN,
                ErrorCode.FORBIDDEN.getCode(), "Access denied")))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(
                "/api/v1/auth/login",
                "/api/v1/auth/2fa/verify",
                "/api/v1/auth/refresh",
                "/api/v1/auth/logout",
                "/api/v1/auth/register-organization",
                "/api/v1/auth/password/forgot",
                "/api/v1/auth/password/reset",
                "/api/v1/auth/password/reset/validate",
                "/api/v1/auth/passkeys/options",
                "/api/v1/auth/passkeys/verify",
                "/api/v1/invites/accept",
                "/api/v1/billing/webhooks/**",
                "/actuator/health",
                "/v3/api-docs/**",
                "/swagger-ui/**",
                "/swagger-ui.html")
            .permitAll()
            .anyRequest().authenticated())
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  private void writeError(HttpServletResponse res, int status, String code, String message)
      throws IOException {
    res.setStatus(status);
    res.setContentType("application/json");
    res.setCharacterEncoding("UTF-8");
    objectMapper.writeValue(res.getWriter(), ApiResponse.error(code, message, null));
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}

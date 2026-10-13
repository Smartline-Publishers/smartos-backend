package com.smartline.smartos.config;

import com.smartline.smartos.security.UserPrincipal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

// @EnableJpaAuditing lives here (not on the application class) so that
// @WebMvcTest slices — which always load the application class but not user
// @Configuration — don't bootstrap JPA auditing infrastructure without entities.
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {

  @Bean
  public AuditorAware<UUID> auditorAware() {
    return () -> {
      Authentication auth = SecurityContextHolder.getContext().getAuthentication();
      if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
        return Optional.ofNullable(principal.getUserId());
      }
      return Optional.empty();
    };
  }
}

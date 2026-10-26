package com.smartline.smartos.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartline.smartos.modules.user.AppUserRepository;
import com.smartline.smartos.security.JwtService;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pins the API datetime contract through real MVC serialization: Instants
 * must render as ISO-8601 strings (Boot 3 behavior), not numeric timestamps.
 */
@WebMvcTest(controllers = JacksonContractTest.ProbeController.class)
@AutoConfigureMockMvc(addFilters = false)
// Nested controllers aren't discovered by slice scanning: register explicitly.
@Import({ JacksonConfig.class, JacksonContractTest.ProbeController.class })
@ActiveProfiles("test")
class JacksonContractTest {

  @Autowired private MockMvc mockMvc;

  // Satisfies the JwtAuthenticationFilter @Component in the slice.
  @MockitoBean private JwtService jwtService;
  // The filter resolves the caller's live role from the user record.
  @MockitoBean private AppUserRepository appUserRepository;

  @RestController
  static class ProbeController {
    @GetMapping("/probe-time")
    public Map<String, Instant> probe() {
      return Map.of("at", Instant.parse("2026-01-02T03:04:05Z"));
    }
  }

  @Test
  void instantsSerializeAsIsoStrings() throws Exception {
    mockMvc.perform(get("/probe-time"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.at").value("2026-01-02T03:04:05Z"));
  }
}

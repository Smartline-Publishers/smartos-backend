package com.smartline.smartos.modules.user;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartline.smartos.security.AuthDtos.LoginRequest;
import com.smartline.smartos.security.AuthDtos.TokenResponse;
import com.smartline.smartos.security.JwtService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class AuthFlowTest {

  @Autowired private MockMvc mockMvc;
  // Plain mapper: the test only serializes the request body, and Boot 4's MVC
  // slice no longer provides a Spring-configured ObjectMapper bean.
  private final ObjectMapper objectMapper = new ObjectMapper();

  // JwtService satisfies the JwtAuthenticationFilter @Component in the slice.
  @MockitoBean private AuthService authService;
  @MockitoBean private JwtService jwtService;

  @Test
  void loginReturnsToken() throws Exception {
    TokenResponse tokens = new TokenResponse("access-123", "refresh-123", "Bearer");
    Mockito.when(authService.login(any(LoginRequest.class))).thenReturn(tokens);

    LoginRequest req = new LoginRequest("admin@acme.test", "secret", UUID.randomUUID());

    mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.accessToken").value("access-123"));
  }
}

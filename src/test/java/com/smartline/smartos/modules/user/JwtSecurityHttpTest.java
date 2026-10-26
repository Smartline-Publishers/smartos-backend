package com.smartline.smartos.modules.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartline.smartos.common.exception.ApiException;
import com.smartline.smartos.modules.organization.Organization;
import com.smartline.smartos.security.AuthDtos.LoginRequest;
import com.smartline.smartos.security.AuthDtos.SessionInfo;
import com.smartline.smartos.security.JwtService;
import com.smartline.smartos.security.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JwtSecurityHttpTest {

  @Autowired private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @MockitoBean private AuthService authService;
  @MockitoBean private JwtService jwtService;
  @MockitoBean private AppUserRepository users;
  @MockitoBean private UserService userService;
  @MockitoBean private TwoFactorService twoFactorService;
  @MockitoBean private Claims claims;

  @Test
  void loginEndpointIsPublicAndInvalidCredentialsReturnUnauthorized() throws Exception {
    LoginRequest request = new LoginRequest("admin@acme.test", "wrong-password", UUID.randomUUID());
    when(authService.login(any(LoginRequest.class), anyString()))
        .thenThrow(new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid credentials"));

    mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void missingAndExpiredAccessTokensReturnUnauthorized() throws Exception {
    mockMvc.perform(get("/api/v1/auth/me"))
        .andExpect(status().isUnauthorized());

    when(jwtService.parse("expired-token")).thenThrow(new JwtException("expired"));
    mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer expired-token"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void validAccessTokenAuthenticatesAndPermissionFailuresRemainForbidden() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID organizationId = UUID.randomUUID();
    Organization organization = new Organization();
    organization.setId(organizationId);
    AppUser user = new AppUser();
    user.setId(userId);
    user.setOrganization(organization);
    user.setRole(Role.VIEWER);
    user.setActive(true);

    when(jwtService.parse("access-token")).thenReturn(claims);
    when(claims.get("type", String.class)).thenReturn("access");
    when(claims.getSubject()).thenReturn(userId.toString());
    when(users.findById(userId)).thenReturn(Optional.of(user));
    when(authService.sessionInfo()).thenReturn(new SessionInfo(
        userId, organizationId, "viewer@acme.test", "Viewer", Role.VIEWER,
        java.util.List.of(Role.VIEWER.name()), java.util.List.of(), java.util.List.of()));

    mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer access-token"))
        .andExpect(status().isOk());

    mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer access-token"))
        .andExpect(status().isForbidden());
    verifyNoInteractions(userService);
  }
}

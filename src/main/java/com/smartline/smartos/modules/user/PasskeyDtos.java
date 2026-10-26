package com.smartline.smartos.modules.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PasskeyDtos {

  private PasskeyDtos() {
  }

  public static class RegistrationOptionsRequest {
    // No fields today; the authenticated caller is the credential owner.
    public RegistrationOptionsRequest() {
    }
  }

  public static class AuthenticationOptionsRequest {
    @Email private String email;

    public AuthenticationOptionsRequest() {
    }

    public String getEmail() {
      return email;
    }

    public void setEmail(String email) {
      this.email = email;
    }
  }

  public static class RegisterRequest {
    @NotBlank private String credentialId;
    @NotBlank private String clientDataJSON;
    @NotBlank private String attestationObject;
    private List<String> transports;
    @Size(max = 100) private String nickname;

    public RegisterRequest() {
    }

    public String getCredentialId() {
      return credentialId;
    }

    public void setCredentialId(String credentialId) {
      this.credentialId = credentialId;
    }

    public String getClientDataJSON() {
      return clientDataJSON;
    }

    public void setClientDataJSON(String clientDataJSON) {
      this.clientDataJSON = clientDataJSON;
    }

    public String getAttestationObject() {
      return attestationObject;
    }

    public void setAttestationObject(String attestationObject) {
      this.attestationObject = attestationObject;
    }

    public List<String> getTransports() {
      return transports;
    }

    public void setTransports(List<String> transports) {
      this.transports = transports;
    }

    public String getNickname() {
      return nickname;
    }

    public void setNickname(String nickname) {
      this.nickname = nickname;
    }
  }

  public static class AssertionRequest {
    @NotBlank private String credentialId;
    @NotBlank private String clientDataJSON;
    @NotBlank private String authenticatorData;
    @NotBlank private String signature;
    private String userHandle;
    private String authenticatorAttachment;

    public AssertionRequest() {
    }

    public String getCredentialId() {
      return credentialId;
    }

    public void setCredentialId(String credentialId) {
      this.credentialId = credentialId;
    }

    public String getClientDataJSON() {
      return clientDataJSON;
    }

    public void setClientDataJSON(String clientDataJSON) {
      this.clientDataJSON = clientDataJSON;
    }

    public String getAuthenticatorData() {
      return authenticatorData;
    }

    public void setAuthenticatorData(String authenticatorData) {
      this.authenticatorData = authenticatorData;
    }

    public String getSignature() {
      return signature;
    }

    public void setSignature(String signature) {
      this.signature = signature;
    }

    public String getUserHandle() {
      return userHandle;
    }

    public void setUserHandle(String userHandle) {
      this.userHandle = userHandle;
    }

    public String getAuthenticatorAttachment() {
      return authenticatorAttachment;
    }

    public void setAuthenticatorAttachment(String authenticatorAttachment) {
      this.authenticatorAttachment = authenticatorAttachment;
    }
  }

  public static class RenameRequest {
    @NotBlank @Size(max = 100, message = "Name must be 100 characters or fewer")
    private String nickname;

    public RenameRequest() {
    }

    public String getNickname() {
      return nickname;
    }

    public void setNickname(String nickname) {
      this.nickname = nickname;
    }
  }

  public static class PasskeyResponse {
    private UUID id;
    private String credentialId;
    private String nickname;
    private Instant createdAt;
    private Instant lastUsedAt;
    private boolean backupEligible;
    private boolean backupState;
    private List<String> transports;

    public PasskeyResponse() {
    }

    public PasskeyResponse(UUID id, String credentialId, String nickname, Instant createdAt,
        Instant lastUsedAt, boolean backupEligible, boolean backupState,
        List<String> transports) {
      this.id = id;
      this.credentialId = credentialId;
      this.nickname = nickname;
      this.createdAt = createdAt;
      this.lastUsedAt = lastUsedAt;
      this.backupEligible = backupEligible;
      this.backupState = backupState;
      this.transports = transports;
    }

    public static PasskeyResponse from(PasskeyCredential credential) {
      List<String> transports = credential.getTransports() == null
          || credential.getTransports().isBlank()
              ? List.of()
              : List.of(credential.getTransports().split(","));
      return new PasskeyResponse(credential.getId(), credential.getCredentialId(),
          credential.getNickname(), credential.getCreatedAt(), credential.getLastUsedAt(),
          credential.isBackupEligible(), credential.isBackupState(), transports);
    }

    public UUID getId() {
      return id;
    }

    public void setId(UUID id) {
      this.id = id;
    }

    public String getCredentialId() {
      return credentialId;
    }

    public void setCredentialId(String credentialId) {
      this.credentialId = credentialId;
    }

    public String getNickname() {
      return nickname;
    }

    public void setNickname(String nickname) {
      this.nickname = nickname;
    }

    public Instant getCreatedAt() {
      return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
      this.createdAt = createdAt;
    }

    public Instant getLastUsedAt() {
      return lastUsedAt;
    }

    public void setLastUsedAt(Instant lastUsedAt) {
      this.lastUsedAt = lastUsedAt;
    }

    public boolean isBackupEligible() {
      return backupEligible;
    }

    public void setBackupEligible(boolean backupEligible) {
      this.backupEligible = backupEligible;
    }

    public boolean isBackupState() {
      return backupState;
    }

    public void setBackupState(boolean backupState) {
      this.backupState = backupState;
    }

    public List<String> getTransports() {
      return transports;
    }

    public void setTransports(List<String> transports) {
      this.transports = transports;
    }
  }
}

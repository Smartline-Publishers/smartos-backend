package com.smartline.smartos.tenant;

import com.smartline.smartos.common.exception.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class TenantContext {

  private static final ThreadLocal<UUID> CURRENT_ORG = new ThreadLocal<>();

  private TenantContext() {
  }

  public static void setCurrentOrg(UUID orgId) {
    CURRENT_ORG.set(orgId);
  }

  public static void clear() {
    CURRENT_ORG.remove();
  }

  public static UUID current() {
    return CURRENT_ORG.get();
  }

  public static UUID require() {
    UUID orgId = CURRENT_ORG.get();
    if (orgId == null) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "MISSING_TENANT", "Missing tenant context (X-Tenant-Id or orgId claim)");
    }
    return orgId;
  }
}

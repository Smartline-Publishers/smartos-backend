package com.smartline.smartos.modules.billing;

import com.smartline.smartos.common.ApiResponse;
import com.smartline.smartos.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/billing")
public class BillingController {

  private final BillingService service;

  public BillingController(BillingService service) {
    this.service = service;
  }

  public static class StripeWebhookRequest {
    @NotBlank private String id;
    @NotBlank private String type;
    private Map<String, Object> data;

    public StripeWebhookRequest() {
    }

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }

    public Map<String, Object> getData() {
      return data;
    }

    public void setData(Map<String, Object> data) {
      this.data = data;
    }
  }

  public static class WebhookResponse {
    private boolean received;
    private boolean duplicate;
    private String plan;

    public WebhookResponse() {
    }

    public WebhookResponse(boolean received, boolean duplicate, String plan) {
      this.received = received;
      this.duplicate = duplicate;
      this.plan = plan;
    }

    public boolean isReceived() {
      return received;
    }

    public void setReceived(boolean received) {
      this.received = received;
    }

    public boolean isDuplicate() {
      return duplicate;
    }

    public void setDuplicate(boolean duplicate) {
      this.duplicate = duplicate;
    }

    public String getPlan() {
      return plan;
    }

    public void setPlan(String plan) {
      this.plan = plan;
    }
  }

  public static class SubscriptionResponse {
    private UUID organizationId;
    private String provider;
    private String status;

    public SubscriptionResponse() {
    }

    public UUID getOrganizationId() {
      return organizationId;
    }

    public void setOrganizationId(UUID organizationId) {
      this.organizationId = organizationId;
    }

    public String getProvider() {
      return provider;
    }

    public void setProvider(String provider) {
      this.provider = provider;
    }

    public String getStatus() {
      return status;
    }

    public void setStatus(String status) {
      this.status = status;
    }
  }

  /**
   * Provider webhook receiver (permit-all; production must verify the Stripe
   * signature before trusting the payload — see README).
   */
  @PostMapping("/webhooks/stripe")
  @ResponseStatus(HttpStatus.OK)
  @PreAuthorize("permitAll()")
  public ApiResponse<WebhookResponse> stripe(@Valid @RequestBody StripeWebhookRequest req) {
    BillingService.WebhookResult result =
        service.handleStripeEvent(req.getId(), req.getType(), req.getData());
    return ApiResponse.ok(new WebhookResponse(true, result.duplicate(), result.plan()));
  }

  @GetMapping("/subscription")
  @PreAuthorize("isAuthenticated()")
  public ApiResponse<SubscriptionResponse> subscription() {
    Subscription subscription = service.subscriptionFor(TenantContext.require());
    SubscriptionResponse r = new SubscriptionResponse();
    r.setOrganizationId(subscription.getOrganization() != null
        ? subscription.getOrganization().getId() : null);
    r.setProvider(subscription.getProvider());
    r.setStatus(subscription.getStatus());
    return ApiResponse.ok(r);
  }
}

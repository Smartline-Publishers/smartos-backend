package com.smartline.smartos.modules.billing;

import com.smartline.smartos.common.exception.ApiException;
import com.smartline.smartos.modules.organization.Organization;
import com.smartline.smartos.modules.organization.Organization.Plan;
import com.smartline.smartos.modules.organization.OrganizationRepository;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Subscription stub: maps provider webhook events onto organization plans.
 * Production wiring swaps the payload parsing for the Stripe SDK plus
 * signature verification (see README); the plan mapping and idempotency
 * semantics stay the same.
 */
@Service
public class BillingService {

  private final SubscriptionRepository subscriptions;
  private final BillingEventRepository events;
  private final OrganizationRepository organizations;

  public BillingService(SubscriptionRepository subscriptions, BillingEventRepository events,
      OrganizationRepository organizations) {
    this.subscriptions = subscriptions;
    this.events = events;
    this.organizations = organizations;
  }

  public record WebhookResult(boolean duplicate, String plan) {
  }

  @Transactional
  public WebhookResult handleStripeEvent(String eventId, String type, Map<String, Object> data) {
    if (eventId == null || eventId.isBlank() || type == null || type.isBlank()) {
      throw ApiException.badRequest("Webhook event requires id and type");
    }
    if (events.existsById(eventId)) {
      return new WebhookResult(true, null);
    }
    events.save(new BillingEvent(eventId, type, data));
    String planName = data != null && data.get("plan") instanceof String s ? s : null;
    UUID organizationId = data != null && data.get("organizationId") instanceof String s
        ? parseUuid(s)
        : null;
    switch (type) {
      case "checkout.session.completed", "customer.subscription.created",
           "customer.subscription.updated" -> {
        if (organizationId == null || planName == null) {
          throw ApiException.badRequest("Subscription events require organizationId and plan");
        }
        Plan plan = parsePlan(planName);
        Organization org = organizations.findById(organizationId)
            .orElseThrow(() -> ApiException.notFound("Organization", organizationId));
        org.setPlan(plan);
        organizations.save(org);
        Subscription subscription = subscriptions.findByOrganizationId(organizationId)
            .orElseGet(() -> {
              Subscription created = new Subscription();
              created.setOrganization(org);
              return created;
            });
        subscription.setProvider("STRIPE_STUB");
        subscription.setStatus("ACTIVE");
        subscriptions.save(subscription);
        return new WebhookResult(false, plan.name());
      }
      case "customer.subscription.deleted" -> {
        if (organizationId == null) {
          throw ApiException.badRequest("Cancellation events require organizationId");
        }
        Organization org = organizations.findById(organizationId)
            .orElseThrow(() -> ApiException.notFound("Organization", organizationId));
        org.setPlan(Plan.FREE);
        organizations.save(org);
        subscriptions.findByOrganizationId(organizationId).ifPresent(subscription -> {
          subscription.setStatus("CANCELED");
          subscriptions.save(subscription);
        });
        return new WebhookResult(false, Plan.FREE.name());
      }
      case "invoice.payment_failed" -> {
        if (organizationId == null) {
          throw ApiException.badRequest("Payment events require organizationId");
        }
        subscriptions.findByOrganizationId(organizationId).ifPresent(subscription -> {
          subscription.setStatus("PAST_DUE");
          subscriptions.save(subscription);
        });
        return new WebhookResult(false, null);
      }
      default -> {
        return new WebhookResult(false, null);
      }
    }
  }

  @Transactional(readOnly = true)
  public Subscription subscriptionFor(UUID organizationId) {
    return subscriptions.findByOrganizationId(organizationId)
        .orElseThrow(() -> ApiException.notFound("Subscription", organizationId));
  }

  private Plan parsePlan(String planName) {
    try {
      return Plan.valueOf(planName.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw ApiException.badRequest("Unknown plan: " + planName);
    }
  }

  private UUID parseUuid(String value) {
    try {
      return UUID.fromString(value.trim());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}

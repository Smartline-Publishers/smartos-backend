package com.smartline.smartos.modules.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartline.smartos.common.exception.ApiException;
import com.smartline.smartos.modules.organization.Organization;
import com.smartline.smartos.modules.organization.Organization.Plan;
import com.smartline.smartos.modules.organization.OrganizationRepository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

  @Mock private SubscriptionRepository subscriptions;
  @Mock private BillingEventRepository events;
  @Mock private OrganizationRepository organizations;

  private BillingService service() {
    return new BillingService(subscriptions, events, organizations);
  }

  @Test
  void firstEventProcessesPlanChange() {
    BillingService service = service();
    Organization org = org(Plan.FREE);
    when(events.existsById("evt-1")).thenReturn(false);
    when(organizations.findById(org.getId())).thenReturn(Optional.of(org));
    when(subscriptions.findByOrganizationId(org.getId())).thenReturn(Optional.empty());
    when(subscriptions.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));

    BillingService.WebhookResult result = service.handleStripeEvent("evt-1",
        "checkout.session.completed", Map.of("organizationId", org.getId().toString(),
            "plan", "GROWTH"));

    assertEquals(false, result.duplicate());
    assertEquals("GROWTH", result.plan());
    assertEquals(Plan.GROWTH, org.getPlan());
  }

  @Test
  void duplicateEventIsAcknowledgedWithoutReprocessing() {
    BillingService service = service();
    when(events.existsById("evt-1")).thenReturn(true);

    BillingService.WebhookResult result = service.handleStripeEvent("evt-1",
        "checkout.session.completed", Map.of());

    assertTrue(result.duplicate());
    verify(organizations, never()).save(any());
    verify(subscriptions, never()).save(any());
  }

  @Test
  void unknownOrganizationReturns404() {
    BillingService service = service();
    UUID unknown = UUID.randomUUID();
    when(events.existsById("evt-9")).thenReturn(false);
    when(organizations.findById(unknown)).thenReturn(Optional.empty());

    ApiException ex = assertThrows(ApiException.class, () -> service.handleStripeEvent("evt-9",
        "customer.subscription.updated",
        Map.of("organizationId", unknown.toString(), "plan", "GROWTH")));

    assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
  }

  @Test
  void cancellationDowngradesToFree() {
    BillingService service = service();
    Organization org = org(Plan.GROWTH);
    Subscription subscription = new Subscription();
    subscription.setId(UUID.randomUUID());
    subscription.setOrganization(org);
    subscription.setStatus("ACTIVE");
    when(events.existsById("evt-2")).thenReturn(false);
    when(organizations.findById(org.getId())).thenReturn(Optional.of(org));
    when(subscriptions.findByOrganizationId(org.getId())).thenReturn(Optional.of(subscription));

    BillingService.WebhookResult result = service.handleStripeEvent("evt-2",
        "customer.subscription.deleted", Map.of("organizationId", org.getId().toString()));

    assertEquals("FREE", result.plan());
    assertEquals(Plan.FREE, org.getPlan());
    assertEquals("CANCELED", subscription.getStatus());
  }

  private Organization org(Plan plan) {
    Organization org = new Organization();
    org.setId(UUID.randomUUID());
    org.setName("Acme");
    org.setSlug("acme-" + UUID.randomUUID().toString().substring(0, 8));
    org.setPlan(plan);
    return org;
  }
}

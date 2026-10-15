package com.smartline.smartos.modules.sales;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartline.smartos.common.exception.ApiException;
import com.smartline.smartos.modules.organization.OrganizationRepository;
import com.smartline.smartos.tenant.TenantContext;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

  @Mock private CustomerRepository customers;
  @Mock private OrganizationRepository organizations;
  @Mock private OrderRepository orders;

  private CustomerService service;
  private UUID orgId;

  @BeforeEach
  void setUp() {
    service = new CustomerService(customers, organizations, orders);
    orgId = UUID.randomUUID();
    TenantContext.setCurrentOrg(orgId);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void deleteCustomerWithOrdersThrows409() {
    Customer customer = new Customer();
    customer.setId(UUID.randomUUID());
    when(customers.findByIdAndOrganization_Id(eq(customer.getId()), eq(orgId)))
        .thenReturn(Optional.of(customer));
    when(orders.existsByOrganization_IdAndCustomerId(eq(orgId), eq(customer.getId())))
        .thenReturn(true);

    ApiException ex = assertThrows(ApiException.class, () -> service.delete(customer.getId()));

    assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    verify(customers, never()).delete(any());
  }

  @Test
  void deleteCustomerWithoutOrdersRemoves() {
    Customer customer = new Customer();
    customer.setId(UUID.randomUUID());
    when(customers.findByIdAndOrganization_Id(eq(customer.getId()), eq(orgId)))
        .thenReturn(Optional.of(customer));
    when(orders.existsByOrganization_IdAndCustomerId(eq(orgId), eq(customer.getId())))
        .thenReturn(false);

    service.delete(customer.getId());

    verify(customers).delete(eq(customer));
  }
}

package com.smartline.smartos.modules.finance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartline.smartos.common.exception.ApiException;
import com.smartline.smartos.modules.finance.FinanceDtos.CreateTransactionRequest;
import com.smartline.smartos.modules.finance.Transaction.TransactionType;
import com.smartline.smartos.modules.organization.Organization;
import com.smartline.smartos.modules.organization.OrganizationRepository;
import com.smartline.smartos.modules.sales.Order;
import com.smartline.smartos.modules.sales.Order.OrderStatus;
import com.smartline.smartos.modules.sales.OrderRepository;
import com.smartline.smartos.tenant.TenantContext;
import java.math.BigDecimal;
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
class FinanceServiceTest {

  @Mock private TransactionRepository transactions;
  @Mock private OrganizationRepository organizations;
  @Mock private OrderRepository orders;

  private FinanceService service;
  private UUID orgId;
  private Organization org;

  @BeforeEach
  void setUp() {
    service = new FinanceService(transactions, organizations, orders);
    orgId = UUID.randomUUID();
    TenantContext.setCurrentOrg(orgId);
    org = new Organization();
    org.setId(orgId);
    org.setName("Acme");
    org.setSlug("acme");
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void manualSaleIsRejected() {
    CreateTransactionRequest req = new CreateTransactionRequest();
    req.setType(TransactionType.SALE);
    req.setAmount(new BigDecimal("10.00"));

    ApiException ex = assertThrows(ApiException.class, () -> service.recordManual(req));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    verify(transactions, never()).save(any());
  }

  @Test
  void refundWithoutOrderIsRejected() {
    CreateTransactionRequest req = new CreateTransactionRequest();
    req.setType(TransactionType.REFUND);
    req.setAmount(new BigDecimal("10.00"));

    assertThrows(ApiException.class, () -> service.recordManual(req));
  }

  @Test
  void refundOnUnfulfilledOrderIsRejected() {
    Order order = new Order();
    order.setId(UUID.randomUUID());
    order.setStatus(OrderStatus.CONFIRMED);
    when(organizations.findById(orgId)).thenReturn(Optional.of(org));
    when(orders.findByIdAndOrganization_Id(eq(order.getId()), eq(orgId)))
        .thenReturn(Optional.of(order));

    CreateTransactionRequest req = new CreateTransactionRequest();
    req.setType(TransactionType.REFUND);
    req.setAmount(new BigDecimal("10.00"));
    req.setReferenceId(order.getId());

    assertThrows(ApiException.class, () -> service.recordManual(req));
  }

  @Test
  void expenseIsRecorded() {
    when(organizations.findById(orgId)).thenReturn(Optional.of(org));
    when(transactions.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

    CreateTransactionRequest req = new CreateTransactionRequest();
    req.setType(TransactionType.EXPENSE);
    req.setAmount(new BigDecimal("250.00"));
    req.setCategory("PRINTING");

    Transaction saved = service.recordManual(req);

    assertEquals(TransactionType.EXPENSE, saved.getType());
    assertEquals(0, new BigDecimal("250.00").compareTo(saved.getAmount()));
  }
}

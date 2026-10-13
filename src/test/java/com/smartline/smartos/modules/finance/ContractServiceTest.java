package com.smartline.smartos.modules.finance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartline.smartos.common.exception.ApiException;
import com.smartline.smartos.modules.book.BookRepository;
import com.smartline.smartos.modules.book.Contributor;
import com.smartline.smartos.modules.book.ContributorRepository;
import com.smartline.smartos.modules.finance.Contract.ContractStatus;
import com.smartline.smartos.modules.finance.RoyaltyDtos.CreateContractRequest;
import com.smartline.smartos.modules.organization.Organization;
import com.smartline.smartos.modules.organization.OrganizationRepository;
import com.smartline.smartos.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
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
class ContractServiceTest {

  @Mock private ContractRepository contracts;
  @Mock private ContributorRepository contributors;
  @Mock private BookRepository books;
  @Mock private OrganizationRepository organizations;
  @Mock private RoyaltyStatementRepository statements;

  private ContractService service;
  private UUID orgId;
  private Organization org;
  private Contributor contributor;

  @BeforeEach
  void setUp() {
    service = new ContractService(contracts, contributors, books, organizations, statements);
    orgId = UUID.randomUUID();
    TenantContext.setCurrentOrg(orgId);
    org = new Organization();
    org.setId(orgId);
    org.setName("Acme");
    org.setSlug("acme");
    contributor = new Contributor();
    contributor.setId(UUID.randomUUID());
    contributor.setOrganization(org);
    contributor.setFullName("Ama Serwaa");
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void terminateMovesActiveForwardOnly() {
    Contract contract = contract(ContractStatus.ACTIVE);
    when(contracts.findByIdAndOrganizationId(eq(contract.getId()), eq(orgId)))
        .thenReturn(Optional.of(contract));
    when(contracts.save(any(Contract.class))).thenAnswer(inv -> inv.getArgument(0));

    assertEquals(ContractStatus.TERMINATED, service.terminate(contract.getId()).getStatus());

    Contract terminated = contract(ContractStatus.TERMINATED);
    when(contracts.findByIdAndOrganizationId(eq(terminated.getId()), eq(orgId)))
        .thenReturn(Optional.of(terminated));

    assertThrows(ApiException.class, () -> service.terminate(terminated.getId()));
  }

  @Test
  void deleteContractWithStatementsThrows409() {
    Contract contract = contract(ContractStatus.ACTIVE);
    when(contracts.findByIdAndOrganizationId(eq(contract.getId()), eq(orgId)))
        .thenReturn(Optional.of(contract));
    when(statements.existsByContractId(eq(contract.getId()))).thenReturn(true);

    ApiException ex = assertThrows(ApiException.class, () -> service.delete(contract.getId()));

    assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    verify(contracts, never()).delete(any());
  }

  @Test
  void createLinksScopedContributor() {
    when(organizations.findById(orgId)).thenReturn(Optional.of(org));
    when(contributors.findByIdAndOrganizationId(eq(contributor.getId()), eq(orgId)))
        .thenReturn(Optional.of(contributor));
    when(contracts.save(any(Contract.class))).thenAnswer(inv -> inv.getArgument(0));

    CreateContractRequest req = new CreateContractRequest();
    req.setContributorId(contributor.getId());
    req.setRoyaltyRate(new BigDecimal("0.1000"));
    req.setStartDate(LocalDate.of(2026, 1, 1));
    Contract saved = service.create(req);

    assertEquals(ContractStatus.ACTIVE, saved.getStatus());
    assertEquals(contributor.getId(), saved.getContributor().getId());
  }

  private Contract contract(ContractStatus status) {
    Contract contract = new Contract();
    contract.setId(UUID.randomUUID());
    contract.setOrganization(org);
    contract.setContributor(contributor);
    contract.setRoyaltyRate(new BigDecimal("0.1000"));
    contract.setStatus(status);
    return contract;
  }
}

package com.smartline.smartos.modules.finance;

import com.smartline.smartos.common.exception.ApiException;
import com.smartline.smartos.modules.audit.Audited;
import com.smartline.smartos.modules.book.Book;
import com.smartline.smartos.modules.book.BookRepository;
import com.smartline.smartos.modules.book.Contributor;
import com.smartline.smartos.modules.book.ContributorRepository;
import com.smartline.smartos.modules.finance.Contract.ContractStatus;
import com.smartline.smartos.modules.finance.RoyaltyDtos.CreateContractRequest;
import com.smartline.smartos.modules.organization.Organization;
import com.smartline.smartos.modules.organization.OrganizationRepository;
import com.smartline.smartos.tenant.TenantContext;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContractService {

  private final ContractRepository contracts;
  private final ContributorRepository contributors;
  private final BookRepository books;
  private final OrganizationRepository organizations;
  private final RoyaltyStatementRepository statements;

  public ContractService(ContractRepository contracts, ContributorRepository contributors,
      BookRepository books, OrganizationRepository organizations,
      RoyaltyStatementRepository statements) {
    this.contracts = contracts;
    this.contributors = contributors;
    this.books = books;
    this.organizations = organizations;
    this.statements = statements;
  }

  @Transactional
  @Audited(action = "royalty.contract.create", entityType = "Contract")
  public Contract create(CreateContractRequest req) {
    UUID orgId = TenantContext.require();
    Organization org = organizations.findById(orgId)
        .orElseThrow(() -> ApiException.notFound("Organization", orgId));
    Contributor contributor = contributors.findByIdAndOrganizationId(req.getContributorId(), orgId)
        .orElseThrow(() -> ApiException.notFound("Contributor", req.getContributorId()));
    Book book = null;
    if (req.getBookId() != null) {
      book = books.findByIdAndOrganizationId(req.getBookId(), orgId)
          .orElseThrow(() -> ApiException.notFound("Book", req.getBookId()));
    }
    if (req.getEndDate() != null && req.getEndDate().isBefore(req.getStartDate())) {
      throw ApiException.badRequest("Contract end date must not precede start date");
    }
    Contract contract = new Contract();
    contract.setOrganization(org);
    contract.setContributor(contributor);
    contract.setBook(book);
    contract.setRoyaltyRate(req.getRoyaltyRate());
    contract.setStartDate(req.getStartDate());
    contract.setEndDate(req.getEndDate());
    contract.setStatus(ContractStatus.ACTIVE);
    return contracts.save(contract);
  }

  @Transactional(readOnly = true)
  public Page<Contract> list(Pageable pageable) {
    return contracts.findByOrganizationId(TenantContext.require(), pageable);
  }

  @Transactional(readOnly = true)
  public Contract get(UUID id) {
    return contracts.findByIdAndOrganizationId(id, TenantContext.require())
        .orElseThrow(() -> ApiException.notFound("Contract", id));
  }

  @Transactional
  @Audited(action = "royalty.contract.terminate", entityType = "Contract")
  public Contract terminate(UUID id) {
    Contract contract = get(id);
    if (contract.getStatus() != ContractStatus.ACTIVE) {
      throw ApiException.badRequest("Only active contracts can be terminated");
    }
    contract.setStatus(ContractStatus.TERMINATED);
    return contracts.save(contract);
  }

  @Transactional
  @Audited(action = "royalty.contract.delete", entityType = "Contract")
  public void delete(UUID id) {
    Contract contract = get(id);
    if (statements.existsByContractId(contract.getId())) {
      throw ApiException.conflict("Contract has royalty statements and cannot be deleted");
    }
    contracts.delete(contract);
  }
}

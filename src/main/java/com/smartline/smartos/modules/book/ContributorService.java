package com.smartline.smartos.modules.book;

import com.smartline.smartos.common.exception.ApiException;
import com.smartline.smartos.modules.audit.Audited;
import com.smartline.smartos.modules.book.BookDtos.CreateContributorRequest;
import com.smartline.smartos.modules.finance.ContractRepository;
import com.smartline.smartos.modules.organization.Organization;
import com.smartline.smartos.modules.organization.OrganizationRepository;
import com.smartline.smartos.tenant.TenantContext;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContributorService {

  private final ContributorRepository contributors;
  private final OrganizationRepository organizations;
  private final ContractRepository contracts;

  public ContributorService(ContributorRepository contributors,
      OrganizationRepository organizations, ContractRepository contracts) {
    this.contributors = contributors;
    this.organizations = organizations;
    this.contracts = contracts;
  }

  @Transactional
  @Audited(action = "contributor.create", entityType = "Contributor")
  public Contributor create(CreateContributorRequest req) {
    UUID orgId = TenantContext.require();
    Organization org = organizations.findById(orgId)
        .orElseThrow(() -> ApiException.notFound("Organization", orgId));
    Contributor c = new Contributor();
    c.setOrganization(org);
    c.setFullName(req.getFullName().trim());
    c.setType(req.getType() != null ? req.getType() : Contributor.ContributorType.AUTHOR);
    c.setEmail(req.getEmail());
    c.setBio(req.getBio());
    return contributors.save(c);
  }

  @Transactional(readOnly = true)
  public Page<Contributor> list(Pageable pageable) {
    return contributors.findByOrganizationId(TenantContext.require(), pageable);
  }

  @Transactional(readOnly = true)
  public Contributor get(UUID id) {
    return contributors.findByIdAndOrganizationId(id, TenantContext.require())
        .orElseThrow(() -> ApiException.notFound("Contributor", id));
  }

  @Transactional
  public void delete(UUID id) {
    Contributor contributor = get(id);
    if (contracts.existsByContributorId(contributor.getId())) {
      throw ApiException.conflict("Contributor has contracts and cannot be deleted");
    }
    contributors.delete(contributor);
  }
}

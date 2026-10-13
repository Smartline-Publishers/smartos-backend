package com.smartline.smartos.modules.sales;

import com.smartline.smartos.common.exception.ApiException;
import com.smartline.smartos.modules.audit.Audited;
import com.smartline.smartos.modules.organization.Organization;
import com.smartline.smartos.modules.organization.OrganizationRepository;
import com.smartline.smartos.modules.sales.Customer.CustomerType;
import com.smartline.smartos.modules.sales.SalesDtos.CreateCustomerRequest;
import com.smartline.smartos.modules.sales.SalesDtos.UpdateCustomerRequest;
import com.smartline.smartos.tenant.TenantContext;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

  private final CustomerRepository customers;
  private final OrganizationRepository organizations;
  private final OrderRepository orders;

  public CustomerService(CustomerRepository customers, OrganizationRepository organizations,
      OrderRepository orders) {
    this.customers = customers;
    this.organizations = organizations;
    this.orders = orders;
  }

  @Transactional
  @Audited(action = "customer.create", entityType = "Customer")
  public Customer create(CreateCustomerRequest req) {
    UUID orgId = TenantContext.require();
    Organization org = organizations.findById(orgId)
        .orElseThrow(() -> ApiException.notFound("Organization", orgId));
    Customer customer = new Customer();
    customer.setOrganization(org);
    customer.setName(req.getName().trim());
    customer.setType(req.getType() != null ? req.getType() : CustomerType.INDIVIDUAL);
    customer.setEmail(req.getEmail());
    customer.setPhone(req.getPhone());
    customer.setAddress(req.getAddress());
    customer.setCreditLimit(req.getCreditLimit() != null ? req.getCreditLimit() : BigDecimal.ZERO);
    return customers.save(customer);
  }

  @Transactional(readOnly = true)
  public Page<Customer> list(String search, Pageable pageable) {
    UUID orgId = TenantContext.require();
    if (search != null && !search.isBlank()) {
      return customers.findByOrganizationIdAndNameContainingIgnoreCase(orgId, search.trim(),
          pageable);
    }
    return customers.findByOrganizationId(orgId, pageable);
  }

  @Transactional(readOnly = true)
  public Customer get(UUID id) {
    return customers.findByIdAndOrganizationId(id, TenantContext.require())
        .orElseThrow(() -> ApiException.notFound("Customer", id));
  }

  @Transactional
  @Audited(action = "customer.update", entityType = "Customer")
  public Customer update(UUID id, UpdateCustomerRequest req) {
    Customer customer = get(id);
    if (req.getName() != null && !req.getName().isBlank()) {
      customer.setName(req.getName().trim());
    }
    if (req.getType() != null) {
      customer.setType(req.getType());
    }
    if (req.getEmail() != null) {
      customer.setEmail(req.getEmail());
    }
    if (req.getPhone() != null) {
      customer.setPhone(req.getPhone());
    }
    if (req.getAddress() != null) {
      customer.setAddress(req.getAddress());
    }
    if (req.getCreditLimit() != null) {
      customer.setCreditLimit(req.getCreditLimit());
    }
    return customers.save(customer);
  }

  @Transactional
  @Audited(action = "customer.delete", entityType = "Customer")
  public void delete(UUID id) {
    UUID orgId = TenantContext.require();
    Customer customer = get(id);
    if (orders.existsByOrganizationIdAndCustomerId(orgId, customer.getId())) {
      throw ApiException.conflict("Customer has orders and cannot be deleted");
    }
    customers.delete(customer);
  }
}

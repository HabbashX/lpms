package com.larv.pharmacy.customer;

import com.larv.pharmacy.audit.AuditAction;
import com.larv.pharmacy.audit.AuditService;
import com.larv.pharmacy.common.dto.PageResponse;
import com.larv.pharmacy.common.exception.BusinessRuleException;
import com.larv.pharmacy.common.exception.CustomerNotFoundException;
import com.larv.pharmacy.common.util.PaginationUtil;
import com.larv.pharmacy.customer.dto.CreateCustomerRequest;
import com.larv.pharmacy.customer.dto.CustomerResponse;
import com.larv.pharmacy.customer.dto.UpdateCustomerRequest;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Customer CRUD. Deletion is a soft delete so financial history survives. */
@Service
public class CustomerService {

    private static final Set<String> SORTABLE = Set.of("id", "name", "phone", "createdAt", "updatedAt");

    private final CustomerRepository customerRepository;
    private final CustomerAccountRepository accountRepository;
    private final AuditService auditService;

    public CustomerService(CustomerRepository customerRepository,
                           CustomerAccountRepository accountRepository,
                           AuditService auditService) {
        this.customerRepository = customerRepository;
        this.accountRepository = accountRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> list(String search, Boolean active, Pageable pageable) {
        Pageable sanitized = PaginationUtil.sanitize(pageable, SORTABLE, "name", Sort.Direction.ASC);
        Page<Customer> page = customerRepository.findAll(buildSpec(search, active), sanitized);
        return PageResponse.from(page, CustomerResponse::from);
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(Long id) {
        return CustomerResponse.from(findOrThrow(id));
    }

    @Transactional
    public CustomerResponse create(CreateCustomerRequest request) {
        Customer customer = new Customer();
        apply(customer, request.name(), request.phone(), request.address(), request.notes());
        customer.setActive(true);
        Customer saved = customerRepository.save(customer);
        accountRepository.save(new CustomerAccount(saved.getId()));

        auditService.record(AuditAction.CREATE_CUSTOMER, "Customer", saved.getId(),
                "Created customer '" + saved.getName() + "'");
        return CustomerResponse.from(saved);
    }

    @Transactional
    public CustomerResponse update(Long id, UpdateCustomerRequest request) {
        Customer customer = findOrThrow(id);
        apply(customer, request.name(), request.phone(), request.address(), request.notes());
        if (request.active() != null) {
            customer.setActive(request.active());
        }
        Customer saved = customerRepository.save(customer);

        auditService.record(AuditAction.UPDATE_CUSTOMER, "Customer", saved.getId(),
                "Updated customer '" + saved.getName() + "'");
        return CustomerResponse.from(saved);
    }

    /** Soft delete: marks the customer inactive, preserving all financial history. */
    @Transactional
    public void deactivate(Long id) {
        Customer customer = findOrThrow(id);
        customer.setActive(false);
        customerRepository.save(customer);

        auditService.record(AuditAction.DELETE_CUSTOMER, "Customer", customer.getId(),
                "Deactivated customer '" + customer.getName() + "'");
    }

    Customer findOrThrow(Long id) {
        return customerRepository.findById(id).orElseThrow(() -> new CustomerNotFoundException(id));
    }

    void assertActive(Customer customer) {
        if (!customer.isActive()) {
            throw new BusinessRuleException("CUSTOMER_INACTIVE",
                    "Customer '" + customer.getName() + "' is inactive");
        }
    }

    private void apply(Customer customer, String name, String phone, String address, String notes) {
        customer.setName(name.trim());
        customer.setPhone(trimToNull(phone));
        customer.setAddress(trimToNull(address));
        customer.setNotes(trimToNull(notes));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Specification<Customer> buildSpec(String search, Boolean active) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (search != null && !search.isBlank()) {
                String pattern = "%" + PaginationUtil.escapeLike(search.trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern, '\\'),
                        cb.like(cb.lower(root.get("phone")), pattern, '\\')));
            }
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

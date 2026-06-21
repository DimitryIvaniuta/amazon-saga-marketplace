package com.github.dimitryivaniuta.marketplace.audit.api;

import com.github.dimitryivaniuta.marketplace.audit.repository.AuditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/** Administrator-only audit query API. */
@RestController
@RequestMapping("/api/admin/audit")
@RequiredArgsConstructor
public class AuditController {
    /** Audit repository. */
    private final AuditRepository repository;

    /** @param aggregateId aggregate id @return immutable audit entries */
    @GetMapping("/{aggregateId}")
    public Flux<AuditRepository.AuditRow> byAggregate(@PathVariable String aggregateId) {
        return repository.find(aggregateId);
    }
}

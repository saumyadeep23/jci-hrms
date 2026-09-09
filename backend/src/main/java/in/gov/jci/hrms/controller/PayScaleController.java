package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.DependencyCheckResponse;
import in.gov.jci.hrms.dto.PayScaleResponse;
import in.gov.jci.hrms.service.PayScaleService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Deprecated, read-only (Phase 1 of the pay_scale_master -> grade_scale_master
 * cutover, V60): the create/update/status/delete endpoints this controller
 * used to expose are removed outright (not left as always-erroring stubs) -
 * with no route registered for POST/PUT/PATCH/DELETE on these paths,
 * Spring's own dispatcher returns 405 Method Not Allowed, which is exactly
 * "read-only" without hand-rolling it. GradeScaleController is the live
 * write path for grade/scale master data now - see PayScaleService's own
 * javadoc for why the underlying service (and pay_scale_master itself)
 * isn't removed yet.
 */
@RestController
@RequestMapping({"/api/pay-scales", "/api/v1/admin/masters/pay-scales"})
@PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
@Deprecated
public class PayScaleController {

    private final PayScaleService payScaleService;

    public PayScaleController(PayScaleService payScaleService) {
        this.payScaleService = payScaleService;
    }

    @GetMapping("/{id}")
    public PayScaleResponse getById(@PathVariable Long id) {
        return payScaleService.getById(id);
    }

    @GetMapping
    public Page<PayScaleResponse> list(Pageable pageable) {
        return payScaleService.list(pageable);
    }

    @GetMapping("/{id}/dependencies")
    public DependencyCheckResponse dependencies(@PathVariable Long id) {
        return payScaleService.dependencies(id);
    }
}

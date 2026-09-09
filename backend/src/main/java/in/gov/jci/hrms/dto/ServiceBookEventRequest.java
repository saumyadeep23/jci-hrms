package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.CareerEventType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * POST /api/v1/employees/:id/service-book - PIMS_SPEC.md operational-features
 * task, Section 3. department/designation/regionalOffice/gradeScale/basicPay
 * are the ledger's own historical snapshot fields (matching
 * EmployeeServiceBook's existing columns) - for PROMOTION/TRANSFER, basicPay
 * (if given) is also applied to employee_employment_categories.regular_basic_pay
 * (see EmployeeServiceBookService). Moving the employee onto a different
 * sanctioned post is a separate action (POST /api/v1/posts/:id/incumbency) -
 * this event only records the career-ledger entry, since no target postId
 * is part of this payload. gradeScaleId points at grade_scale_master
 * (V60) - the old payScaleId/pay_scale_master linkage was dropped.
 */
public record ServiceBookEventRequest(
        @NotNull LocalDate eventDate,
        @NotNull CareerEventType eventType,
        @Size(max = 100) String orderNumber,
        LocalDate orderDate,
        Long departmentId,
        Long designationId,
        Long regionalOfficeId,
        Long gradeScaleId,
        BigDecimal basicPay,
        @Size(max = 1000) String remarks
) {
}

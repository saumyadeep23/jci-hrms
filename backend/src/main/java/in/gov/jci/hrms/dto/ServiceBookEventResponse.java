package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.EmployeeServiceBook;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ServiceBookEventResponse(
        Long id,
        LocalDate eventDate,
        String eventType,
        String orderNumber,
        LocalDate orderDate,
        String departmentName,
        String designationTitle,
        String regionalOfficeName,
        BigDecimal basicPay,
        /** EL encashment structured metadata (V64) - null for every non-encashment event type. */
        BigDecimal daysEncashed,
        BigDecimal daRate,
        BigDecimal grossAmount,
        String eventDescription,
        String remarks,
        boolean isMigrated
) {
    public static ServiceBookEventResponse from(EmployeeServiceBook event) {
        return new ServiceBookEventResponse(
                event.getId(),
                event.getEventDate(),
                event.getEventType(),
                event.getOrderNumber(),
                event.getOrderDate(),
                event.getDepartment() != null ? event.getDepartment().getName() : null,
                event.getDesignation() != null ? event.getDesignation().getTitle() : null,
                event.getRegionalOffice() != null ? event.getRegionalOffice().getName() : null,
                event.getBasicPay(),
                event.getDaysEncashed(),
                event.getDaRate(),
                event.getGrossAmount(),
                event.getEventDescription(),
                event.getRemarks(),
                event.isMigrated()
        );
    }
}

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
                event.getEventDescription(),
                event.getRemarks(),
                event.isMigrated()
        );
    }
}

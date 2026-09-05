package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record StagingServiceBookRequest(
        @NotBlank @Size(max = 50) String employeeCode,
        @NotNull LocalDate eventDate,
        @NotBlank @Size(max = 50) String eventType,
        @Size(max = 100) String orderNumber,
        LocalDate orderDate,
        @Size(max = 150) String fromDesignation,
        @Size(max = 150) String toDesignation,
        @Size(max = 150) String fromDepartment,
        @Size(max = 150) String toDepartment,
        @Size(max = 150) String fromRo,
        @Size(max = 150) String toRo,
        BigDecimal basicPay,
        String description
) {
}

package in.gov.jci.hrms.dto;

import java.util.List;

public record LegacyMigrationStatusResponse(
        List<CategoryStatus> categories
) {
}

package in.gov.jci.hrms.dto;

import java.util.List;

public record IncrementBatchProcessResponse(int processedCount, int skippedCount, List<String> skippedReasons) {
}

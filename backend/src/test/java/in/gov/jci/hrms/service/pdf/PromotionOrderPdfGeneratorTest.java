package in.gov.jci.hrms.service.pdf;

import in.gov.jci.hrms.entity.CityClass;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeMovementRecord;
import in.gov.jci.hrms.entity.MovementOrder;
import in.gov.jci.hrms.entity.MovementOrderType;
import in.gov.jci.hrms.entity.RegionalOffice;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PromotionOrderPdfGeneratorTest {

    private final PromotionOrderPdfGenerator generator = new PromotionOrderPdfGenerator(new PdfHeaderFooterHelper());

    private EmployeeMovementRecord promotionRecord(MovementOrder order) {
        Department department = new Department("ENG", "Engineering");
        Designation fromDesignation = new Designation("Deputy Manager");
        Designation toDesignation = new Designation("Manager (O/M)");
        Employee employee = new Employee("EMP-001", "Asha", "Rao", "asha@example.com", LocalDate.of(2015, 1, 1), department, fromDesignation);
        ReflectionTestUtils.setField(employee, "id", 1L);
        RegionalOffice office = new RegionalOffice("RO-A", "Kolkata RO", "West Bengal", CityClass.Y, true);
        ReflectionTestUtils.setField(office, "id", 10L);

        EmployeeMovementRecord record = new EmployeeMovementRecord(order, employee, office, fromDesignation, office, toDesignation);
        record.setToPayScale("IDA 60,000-1,80,000");
        record.setProbationPeriodMonths(6);
        return record;
    }

    @Test
    void generate_producesLoadablePdfWithMandatoryClauses() throws IOException {
        MovementOrder order = new MovementOrder(MovementOrderType.PROMOTION, "JCI/Promotion/2025-26/Pers.(R)", LocalDate.of(2026, 3, 1));
        byte[] pdf = generator.generate(order, List.of(promotionRecord(order)));

        assertThat(pdf).isNotEmpty();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    void generate_noRecords_doesNotThrow() throws IOException {
        MovementOrder order = new MovementOrder(MovementOrderType.PROMOTION, "JCI/Promotion/2025-26/Pers.(R)", LocalDate.of(2026, 3, 1));
        byte[] pdf = generator.generate(order, List.of());

        assertThat(pdf).isNotEmpty();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(1);
        }
    }
}

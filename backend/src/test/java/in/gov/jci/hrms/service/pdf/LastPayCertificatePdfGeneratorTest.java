package in.gov.jci.hrms.service.pdf;

import in.gov.jci.hrms.entity.CityClass;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeMovementRecord;
import in.gov.jci.hrms.entity.MovementLpcRecord;
import in.gov.jci.hrms.entity.MovementOrder;
import in.gov.jci.hrms.entity.MovementOrderType;
import in.gov.jci.hrms.entity.RegionalOffice;
import in.gov.jci.hrms.entity.SessionType;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class LastPayCertificatePdfGeneratorTest {

    private final LastPayCertificatePdfGenerator generator = new LastPayCertificatePdfGenerator(new PdfHeaderFooterHelper());

    @Test
    void generate_producesLoadablePdf() throws IOException {
        Department department = new Department("ENG", "Engineering");
        Designation designation = new Designation("Manager");
        Employee employee = new Employee("EMP-001", "Asha", "Rao", "asha@example.com", LocalDate.of(2015, 1, 1), department, designation);
        ReflectionTestUtils.setField(employee, "id", 1L);
        RegionalOffice fromOffice = new RegionalOffice("RO-A", "Kolkata RO", "West Bengal", CityClass.Y, true);
        RegionalOffice toOffice = new RegionalOffice("RO-B", "Mumbai RO", "Maharashtra", CityClass.X, true);
        MovementOrder order = new MovementOrder(MovementOrderType.TRANSFER, "JCI/Pers./HO/Transfer/2025-26/94", LocalDate.of(2026, 3, 1));
        EmployeeMovementRecord movement = new EmployeeMovementRecord(order, employee, fromOffice, designation, toOffice, designation);
        movement.setReleaseOrderRef("JCI/Pers./HO/Release/2025-26");
        movement.setReleaseDate(LocalDate.of(2026, 3, 14));
        movement.setReleaseSession(SessionType.AFTERNOON);
        ReflectionTestUtils.setField(movement, "id", 500L);

        MovementLpcRecord lpc = new MovementLpcRecord(movement, employee, "LPC/500/2026",
                new BigDecimal("45000.00"), new BigDecimal("18450.00"), new BigDecimal("10800.00"), new BigDecimal("7614.00"),
                20, 15, LocalDate.of(2026, 3, 14), SessionType.AFTERNOON);
        lpc.setCpfAdvanceBalance(new BigDecimal("12000.00"));
        lpc.setFestivalAdvanceBalance(new BigDecimal("3000.00"));
        ReflectionTestUtils.setField(lpc, "generatedAt", java.time.Instant.parse("2026-03-15T05:00:00Z"));

        byte[] pdf = generator.generate(lpc);

        assertThat(pdf).isNotEmpty();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(1);
        }
    }
}

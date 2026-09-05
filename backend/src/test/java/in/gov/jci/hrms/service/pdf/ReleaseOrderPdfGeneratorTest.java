package in.gov.jci.hrms.service.pdf;

import in.gov.jci.hrms.entity.CityClass;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeMovementRecord;
import in.gov.jci.hrms.entity.MovementOrder;
import in.gov.jci.hrms.entity.MovementOrderType;
import in.gov.jci.hrms.entity.RegionalOffice;
import in.gov.jci.hrms.entity.SessionType;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ReleaseOrderPdfGeneratorTest {

    private final ReleaseOrderPdfGenerator generator = new ReleaseOrderPdfGenerator(new PdfHeaderFooterHelper());

    @Test
    void generate_releasedRecord_producesLoadablePdf() throws IOException {
        Department department = new Department("ENG", "Engineering");
        Designation designation = new Designation("Manager");
        Employee employee = new Employee("EMP-001", "Asha", "Rao", "asha@example.com", LocalDate.of(2015, 1, 1), department, designation);
        ReflectionTestUtils.setField(employee, "id", 1L);
        RegionalOffice fromOffice = new RegionalOffice("RO-A", "Kolkata RO", "West Bengal", CityClass.Y, true);
        RegionalOffice toOffice = new RegionalOffice("RO-B", "Mumbai RO", "Maharashtra", CityClass.X, true);

        MovementOrder order = new MovementOrder(MovementOrderType.TRANSFER, "JCI/Pers./HO/Transfer/2025-26/94", LocalDate.of(2026, 3, 1));
        EmployeeMovementRecord record = new EmployeeMovementRecord(order, employee, fromOffice, designation, toOffice, designation);
        record.setReleaseOrderRef("JCI/Pers./HO/Release/2025-26");
        record.setReleaseDate(LocalDate.of(2026, 3, 14));
        record.setReleaseSession(SessionType.AFTERNOON);

        byte[] pdf = generator.generate(record);

        assertThat(pdf).isNotEmpty();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(1);
        }
    }
}

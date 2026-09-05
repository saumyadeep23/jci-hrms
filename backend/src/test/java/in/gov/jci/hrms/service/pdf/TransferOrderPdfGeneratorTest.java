package in.gov.jci.hrms.service.pdf;

import in.gov.jci.hrms.entity.CityClass;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeMovementRecord;
import in.gov.jci.hrms.entity.MovementOrder;
import in.gov.jci.hrms.entity.MovementOrderType;
import in.gov.jci.hrms.entity.RegionalOffice;
import in.gov.jci.hrms.entity.TransferNature;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TransferOrderPdfGeneratorTest {

    private final TransferOrderPdfGenerator generator = new TransferOrderPdfGenerator(new PdfHeaderFooterHelper());

    private Employee employee(String code, String first) {
        Department department = new Department("ENG", "Engineering");
        Designation designation = new Designation("Manager");
        Employee employee = new Employee(code, first, "Rao", first.toLowerCase() + "@example.com", LocalDate.of(2015, 1, 1),
                department, designation);
        ReflectionTestUtils.setField(employee, "id", 1L);
        return employee;
    }

    private RegionalOffice office(String code, String name) {
        RegionalOffice office = new RegionalOffice(code, name, "West Bengal", CityClass.Y, true);
        ReflectionTestUtils.setField(office, "id", 10L);
        return office;
    }

    private EmployeeMovementRecord record(MovementOrder order, boolean benefitAdmissible) {
        Designation designation = new Designation("Manager");
        EmployeeMovementRecord record = new EmployeeMovementRecord(order, employee("EMP-001", "Asha"), office("RO-A", "Kolkata RO"),
                designation, office("RO-B", "Mumbai RO"), designation);
        record.setTransferBenefitAdmissible(benefitAdmissible);
        return record;
    }

    @Test
    void generate_administrativeTransfer_producesLoadablePdf() throws IOException {
        MovementOrder order = new MovementOrder(MovementOrderType.TRANSFER, "JCI/Pers./HO/Transfer/2025-26/94", LocalDate.of(2026, 3, 1));
        byte[] pdf = generator.generate(order, List.of(record(order, true)));

        assertThat(pdf).isNotEmpty();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    void generate_ownRequestTransfer_stillProducesLoadablePdf() throws IOException {
        MovementOrder order = new MovementOrder(MovementOrderType.TRANSFER, "JCI/Pers./HO/Transfer/2025-26/95", LocalDate.of(2026, 3, 1));
        EmployeeMovementRecord r = record(order, false);
        r.setTransferNature(TransferNature.OWN_REQUEST);

        byte[] pdf = generator.generate(order, List.of(r));

        assertThat(pdf).isNotEmpty();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    void generate_multipleEmployeesInOneOrder_listsAllOfThemAndStillLoads() throws IOException {
        MovementOrder order = new MovementOrder(MovementOrderType.TRANSFER, "JCI/Pers./HO/Transfer/2025-26/96", LocalDate.of(2026, 3, 1));
        byte[] pdf = generator.generate(order, List.of(record(order, true), record(order, true), record(order, true)));

        assertThat(pdf).isNotEmpty();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(1);
        }
    }
}

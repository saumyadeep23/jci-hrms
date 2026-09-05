package in.gov.jci.hrms.service;

import in.gov.jci.hrms.entity.CityClass;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.DepartmentalPurchaseCentre;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.RegionalOffice;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class GeofenceServiceTest {

    private final GeofenceService geofenceService = new GeofenceService();

    private Employee employeeWithDpc(DepartmentalPurchaseCentre dpc) {
        Department department = new Department("ENG", "Engineering");
        Designation designation = new Designation("Field Officer");
        Employee employee = new Employee("EMP-001", "Asha", "Rao", "asha.rao@example.com",
                LocalDate.of(2024, 1, 15), department, designation);
        employee.setDepartmentalPurchaseCentre(dpc);
        return employee;
    }

    private DepartmentalPurchaseCentre dpcWithGeofence(BigDecimal lat, BigDecimal lon, Integer radiusMeters) {
        RegionalOffice ro = new RegionalOffice("RO-DEL", "Delhi RO", "Delhi", CityClass.X, true);
        DepartmentalPurchaseCentre dpc = new DepartmentalPurchaseCentre(ro, "DPC-DEL-01", "Delhi DPC 1",
                "New Delhi", "Delhi", true);
        dpc.setLatitude(lat);
        dpc.setLongitude(lon);
        dpc.setGeofenceRadiusMeters(radiusMeters);
        return dpc;
    }

    @Test
    void distanceMeters_betweenSameCoordinates_isZero() {
        BigDecimal lat = new BigDecimal("28.613900");
        BigDecimal lon = new BigDecimal("77.209000");

        double distance = geofenceService.distanceMeters(lat, lon, lat, lon);

        assertThat(distance).isCloseTo(0.0, within(0.001));
    }

    @Test
    void distanceMeters_betweenKnownCities_matchesApproximateKnownDistance() {
        // Delhi (28.6139, 77.2090) to Agra (27.1767, 78.0081) is ~178 km great-circle.
        double distance = geofenceService.distanceMeters(
                new BigDecimal("28.6139"), new BigDecimal("77.2090"),
                new BigDecimal("27.1767"), new BigDecimal("78.0081"));

        assertThat(distance).isBetween(170_000.0, 185_000.0);
    }

    @Test
    void isWithinGeofence_whenPunchIsInsideRadius_returnsTrue() {
        DepartmentalPurchaseCentre dpc = dpcWithGeofence(new BigDecimal("28.613900"), new BigDecimal("77.209000"), 200);
        Employee employee = employeeWithDpc(dpc);

        boolean result = geofenceService.isWithinGeofence(employee, new BigDecimal("28.613950"), new BigDecimal("77.209050"));

        assertThat(result).isTrue();
    }

    @Test
    void isWithinGeofence_whenPunchIsOutsideRadius_returnsFalse() {
        DepartmentalPurchaseCentre dpc = dpcWithGeofence(new BigDecimal("28.613900"), new BigDecimal("77.209000"), 200);
        Employee employee = employeeWithDpc(dpc);

        // ~1.1 degrees of longitude at this latitude is roughly 100+ km away.
        boolean result = geofenceService.isWithinGeofence(employee, new BigDecimal("28.613900"), new BigDecimal("78.209000"));

        assertThat(result).isFalse();
    }

    @Test
    void isWithinGeofence_whenEmployeeHasNoDpc_returnsTrue() {
        Employee employee = employeeWithDpc(null);

        boolean result = geofenceService.isWithinGeofence(employee, new BigDecimal("28.6139"), new BigDecimal("77.2090"));

        assertThat(result).isTrue();
    }

    @Test
    void isWithinGeofence_whenDpcHasNoGeofenceConfigured_returnsTrue() {
        RegionalOffice ro = new RegionalOffice("RO-DEL", "Delhi RO", "Delhi", CityClass.X, true);
        DepartmentalPurchaseCentre dpcWithoutGeofence = new DepartmentalPurchaseCentre(ro, "DPC-DEL-02",
                "Delhi DPC 2", "New Delhi", "Delhi", true);
        Employee employee = employeeWithDpc(dpcWithoutGeofence);

        boolean result = geofenceService.isWithinGeofence(employee, new BigDecimal("28.6139"), new BigDecimal("77.2090"));

        assertThat(result).isTrue();
    }

    @Test
    void isWithinGeofence_withGenerousRadius_returnsTrueForNearbyPoint() {
        DepartmentalPurchaseCentre dpc = dpcWithGeofence(new BigDecimal("28.613900"), new BigDecimal("77.209000"), 1_000_000);
        Employee employee = employeeWithDpc(dpc);

        boolean result = geofenceService.isWithinGeofence(employee, new BigDecimal("28.700000"), new BigDecimal("77.300000"));

        assertThat(result).isTrue();
    }

    // ---- RO fallback (no DPC assigned) ----

    private RegionalOffice regionalOfficeWithGeofence(BigDecimal lat, BigDecimal lon, BigDecimal radiusMeters) {
        RegionalOffice ro = new RegionalOffice("01", "Kolkata HO", "West Bengal", CityClass.X, true,
                in.gov.jci.hrms.entity.OfficeType.HEAD_OFFICE, null, null, null, null, null,
                lat, lon, radiusMeters, BigDecimal.ZERO, true);
        return ro;
    }

    private Employee employeeWithRo(RegionalOffice ro) {
        Department department = new Department("ENG", "Engineering");
        Designation designation = new Designation("Field Officer");
        Employee employee = new Employee("EMP-002", "Ravi", "Kumar", "ravi.kumar@example.com",
                LocalDate.of(2024, 1, 15), department, designation);
        employee.setRegionalOffice(ro);
        return employee;
    }

    @Test
    void isWithinGeofence_whenNoDpcButRoHasGeofence_fallsBackToRo() {
        RegionalOffice ro = regionalOfficeWithGeofence(new BigDecimal("22.572600"), new BigDecimal("88.363900"), new BigDecimal("200.00"));
        Employee employee = employeeWithRo(ro);

        boolean insideResult = geofenceService.isWithinGeofence(employee, new BigDecimal("22.572650"), new BigDecimal("88.363950"));
        boolean outsideResult = geofenceService.isWithinGeofence(employee, new BigDecimal("22.572600"), new BigDecimal("89.363900"));

        assertThat(insideResult).isTrue();
        assertThat(outsideResult).isFalse();
    }

    @Test
    void isWithinGeofence_whenDpcHasGeofenceAndRoAlsoHasGeofence_dpcTakesPriority() {
        RegionalOffice ro = regionalOfficeWithGeofence(new BigDecimal("22.572600"), new BigDecimal("88.363900"), new BigDecimal("200.00"));
        DepartmentalPurchaseCentre dpc = new DepartmentalPurchaseCentre(ro, "0001", "Test DPC", "New Delhi", "Delhi", true);
        dpc.setLatitude(new BigDecimal("28.613900"));
        dpc.setLongitude(new BigDecimal("77.209000"));
        dpc.setGeofenceRadiusMeters(200);
        Department department = new Department("ENG", "Engineering");
        Designation designation = new Designation("Field Officer");
        Employee employee = new Employee("EMP-003", "Priya", "Singh", "priya.singh@example.com",
                LocalDate.of(2024, 1, 15), department, designation);
        employee.setRegionalOffice(ro);
        employee.setDepartmentalPurchaseCentre(dpc);

        // Within the DPC's radius but far outside the RO's - DPC wins, so this is still "within".
        boolean result = geofenceService.isWithinGeofence(employee, new BigDecimal("28.613950"), new BigDecimal("77.209050"));

        assertThat(result).isTrue();
    }

    // ---- geofence exemption ----

    @Test
    void isWithinGeofence_whenEmployeeIsGeofenceExempted_returnsTrueEvenOutsideRadius() {
        DepartmentalPurchaseCentre dpc = dpcWithGeofence(new BigDecimal("28.613900"), new BigDecimal("77.209000"), 200);
        Employee employee = employeeWithDpc(dpc);
        employee.setGeofenceExempted(true);

        boolean result = geofenceService.isWithinGeofence(employee, new BigDecimal("28.613900"), new BigDecimal("78.209000"));

        assertThat(result).isTrue();
    }
}

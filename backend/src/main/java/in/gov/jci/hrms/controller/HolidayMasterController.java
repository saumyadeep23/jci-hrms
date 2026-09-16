package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.HolidayBulkUploadResult;
import in.gov.jci.hrms.dto.HolidayMasterCreateRequest;
import in.gov.jci.hrms.dto.HolidayMasterRow;
import in.gov.jci.hrms.dto.HolidayMasterUpdateRequest;
import in.gov.jci.hrms.service.HolidayMasterService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Year;
import java.util.List;

/**
 * State-wise Yearly Holiday &amp; RH Management Publisher (ALMS). Read is
 * open to any authenticated employee (matching /api/holidays' own
 * visibility rule); every mutation is HR_ADMIN/SUPER_ADMIN only.
 */
@RestController
@RequestMapping("/api/v1/master/holidays")
public class HolidayMasterController {

    private final HolidayMasterService holidayMasterService;

    public HolidayMasterController(HolidayMasterService holidayMasterService) {
        this.holidayMasterService = holidayMasterService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<HolidayMasterRow> list(@RequestParam(required = false) Integer year,
                                         @RequestParam(required = false) String stateCode,
                                         @RequestParam(required = false) String type) {
        int resolvedYear = year != null ? year : Year.now().getValue();
        return holidayMasterService.list(resolvedYear, stateCode, type);
    }

    @PostMapping
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<List<HolidayMasterRow>> create(@Valid @RequestBody HolidayMasterCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(holidayMasterService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public HolidayMasterRow update(@PathVariable Long id, @Valid @RequestBody HolidayMasterUpdateRequest request) {
        return holidayMasterService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        holidayMasterService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/bulk-upload")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public HolidayBulkUploadResult bulkUpload(@RequestPart("file") MultipartFile file) {
        return holidayMasterService.bulkUpload(file);
    }
}

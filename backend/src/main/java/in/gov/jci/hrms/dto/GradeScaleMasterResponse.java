package in.gov.jci.hrms.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import in.gov.jci.hrms.entity.Cadre;
import in.gov.jci.hrms.entity.GradeScaleMaster;
import in.gov.jci.hrms.entity.ScaleType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record GradeScaleMasterResponse(
        Long id,
        String scaleCode,
        Cadre cadre,
        int hierarchyLevel,
        boolean boardLevel,
        BigDecimal minimumBasic,
        BigDecimal maximumBasic,
        String idaScaleLabel,
        BigDecimal incrementRate,
        ScaleType scaleType,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MM-yyyy") LocalDate effectiveDate,
        BigDecimal contractualLumpsum,
        BigDecimal outsourcedCtc,
        boolean active
) {
    public static GradeScaleMasterResponse from(GradeScaleMaster g) {
        return new GradeScaleMasterResponse(
                g.getId(), g.getScaleCode(), g.getCadre(), g.getHierarchyLevel(), g.isBoardLevel(),
                g.getMinimumBasic(), g.getMaximumBasic(), g.idaScaleLabel(), g.getIncrementRate(), g.getScaleType(),
                g.getEffectiveDate(), g.getContractualLumpsum(), g.getOutsourcedCtc(), g.isActive());
    }
}

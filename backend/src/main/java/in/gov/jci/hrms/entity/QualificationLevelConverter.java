package in.gov.jci.hrms.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * employee_qualifications.qualification_level stores values that aren't
 * valid Java identifiers ("10TH_SECONDARY", "12TH_HIGHER_SECONDARY"), so
 * @Enumerated(EnumType.STRING)'s exact-name matching can't be used directly -
 * mirrors StateTypeConverter's approach for the same kind of mismatch.
 */
@Converter
public class QualificationLevelConverter implements AttributeConverter<QualificationLevel, String> {

    @Override
    public String convertToDatabaseColumn(QualificationLevel attribute) {
        if (attribute == null) {
            return null;
        }
        return switch (attribute) {
            case TENTH_SECONDARY -> "10TH_SECONDARY";
            case TWELFTH_HIGHER_SECONDARY -> "12TH_HIGHER_SECONDARY";
            case DIPLOMA -> "DIPLOMA";
            case GRADUATION -> "GRADUATION";
            case POST_GRADUATION -> "POST_GRADUATION";
            case DOCTORATE_PHD -> "DOCTORATE_PHD";
            case PROFESSIONAL_CERTIFICATION -> "PROFESSIONAL_CERTIFICATION";
            case OTHER -> "OTHER";
        };
    }

    @Override
    public QualificationLevel convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return switch (dbData) {
            case "10TH_SECONDARY" -> QualificationLevel.TENTH_SECONDARY;
            case "12TH_HIGHER_SECONDARY" -> QualificationLevel.TWELFTH_HIGHER_SECONDARY;
            case "DIPLOMA" -> QualificationLevel.DIPLOMA;
            case "GRADUATION" -> QualificationLevel.GRADUATION;
            case "POST_GRADUATION" -> QualificationLevel.POST_GRADUATION;
            case "DOCTORATE_PHD" -> QualificationLevel.DOCTORATE_PHD;
            case "PROFESSIONAL_CERTIFICATION" -> QualificationLevel.PROFESSIONAL_CERTIFICATION;
            case "OTHER" -> QualificationLevel.OTHER;
            default -> throw new IllegalArgumentException("Unknown qualification_level value: " + dbData);
        };
    }
}

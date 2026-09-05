package in.gov.jci.hrms.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** employees.blood_group stores "A+"/"A-"/... - mirrors StateTypeConverter's approach. */
@Converter
public class BloodGroupConverter implements AttributeConverter<BloodGroup, String> {

    @Override
    public String convertToDatabaseColumn(BloodGroup attribute) {
        if (attribute == null) {
            return null;
        }
        return switch (attribute) {
            case A_POSITIVE -> "A+";
            case A_NEGATIVE -> "A-";
            case B_POSITIVE -> "B+";
            case B_NEGATIVE -> "B-";
            case AB_POSITIVE -> "AB+";
            case AB_NEGATIVE -> "AB-";
            case O_POSITIVE -> "O+";
            case O_NEGATIVE -> "O-";
        };
    }

    @Override
    public BloodGroup convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return switch (dbData) {
            case "A+" -> BloodGroup.A_POSITIVE;
            case "A-" -> BloodGroup.A_NEGATIVE;
            case "B+" -> BloodGroup.B_POSITIVE;
            case "B-" -> BloodGroup.B_NEGATIVE;
            case "AB+" -> BloodGroup.AB_POSITIVE;
            case "AB-" -> BloodGroup.AB_NEGATIVE;
            case "O+" -> BloodGroup.O_POSITIVE;
            case "O-" -> BloodGroup.O_NEGATIVE;
            default -> throw new IllegalArgumentException("Unknown blood_group value: " + dbData);
        };
    }
}

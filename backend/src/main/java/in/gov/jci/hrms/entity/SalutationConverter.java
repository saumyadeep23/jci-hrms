package in.gov.jci.hrms.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** employees.salutation stores "Mr."/"Ms."/"Mrs."/"Dr." - mirrors StateTypeConverter's approach. */
@Converter
public class SalutationConverter implements AttributeConverter<Salutation, String> {

    @Override
    public String convertToDatabaseColumn(Salutation attribute) {
        if (attribute == null) {
            return null;
        }
        return switch (attribute) {
            case MR -> "Mr.";
            case MS -> "Ms.";
            case MRS -> "Mrs.";
            case DR -> "Dr.";
        };
    }

    @Override
    public Salutation convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return switch (dbData) {
            case "Mr." -> Salutation.MR;
            case "Ms." -> Salutation.MS;
            case "Mrs." -> Salutation.MRS;
            case "Dr." -> Salutation.DR;
            default -> throw new IllegalArgumentException("Unknown salutation value: " + dbData);
        };
    }
}

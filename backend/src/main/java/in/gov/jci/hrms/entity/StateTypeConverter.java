package in.gov.jci.hrms.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * state_master.state_type stores human-readable values ("State", "Union
 * Territory", "NCT" for Delhi) from the legacy Oracle state-data import, not
 * the enum constant names - this converter bridges that instead of relying
 * on @Enumerated(EnumType.STRING)'s exact-name matching.
 */
@Converter
public class StateTypeConverter implements AttributeConverter<StateType, String> {

    @Override
    public String convertToDatabaseColumn(StateType attribute) {
        if (attribute == null) {
            return null;
        }
        return switch (attribute) {
            case STATE -> "State";
            case UNION_TERRITORY -> "Union Territory";
            case NATIONAL_CAPITAL_TERRITORY -> "NCT";
        };
    }

    @Override
    public StateType convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return switch (dbData) {
            case "State" -> StateType.STATE;
            case "Union Territory" -> StateType.UNION_TERRITORY;
            case "NCT" -> StateType.NATIONAL_CAPITAL_TERRITORY;
            default -> throw new IllegalArgumentException("Unknown state_type value: " + dbData);
        };
    }
}

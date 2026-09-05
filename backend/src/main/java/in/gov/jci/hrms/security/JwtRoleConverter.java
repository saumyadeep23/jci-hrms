package in.gov.jci.hrms.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Extracts Keycloak-shaped realm ("realm_access.roles") and resource/client
 * ("resource_access.*.roles") roles into ROLE_-prefixed GrantedAuthorities.
 * Both claim shapes are merged - a role granted at either level is honored.
 */
public class JwtRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String ROLE_PREFIX = "ROLE_";

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Set<GrantedAuthority> authorities = new HashSet<>();
        addRealmRoles(jwt, authorities);
        addResourceRoles(jwt, authorities);
        return authorities;
    }

    @SuppressWarnings("unchecked")
    private void addRealmRoles(Jwt jwt, Set<GrantedAuthority> authorities) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null) {
            return;
        }
        Object roles = realmAccess.get("roles");
        if (roles instanceof Collection<?> roleCollection) {
            roleCollection.forEach(role -> authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + role)));
        }
    }

    @SuppressWarnings("unchecked")
    private void addResourceRoles(Jwt jwt, Set<GrantedAuthority> authorities) {
        Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
        if (resourceAccess == null) {
            return;
        }
        for (Object clientAccess : resourceAccess.values()) {
            if (clientAccess instanceof Map<?, ?> clientAccessMap) {
                Object roles = clientAccessMap.get("roles");
                if (roles instanceof Collection<?> roleCollection) {
                    roleCollection.forEach(role -> authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + role)));
                }
            }
        }
    }
}

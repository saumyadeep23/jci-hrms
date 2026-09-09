package in.gov.jci.hrms.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Stateless JWT resource-server security. Method-level authorization
 * (@PreAuthorize on controllers) is where the real access-control decisions
 * live - see the six ROLE_* constants documented on each controller. This
 * class only establishes: no HTTP session, JWT bearer-token authentication,
 * and how a JWT's roles/employee identity map onto Spring Security types.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final String issuerUri;
    private final String localDevSecret;

    public SecurityConfig(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String issuerUri,
                           @Value("${security.jwt.local-dev-secret}") String localDevSecret) {
        this.issuerUri = issuerUri;
        this.localDevSecret = localDevSecret;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Customizer.withDefaults() has Spring Security look up the sole
                // CorsConfigurationSource bean in the context (below) itself.
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // CorsFilter (registered by .cors() above) already intercepts and
                        // completes preflight requests before they'd reach here, but that
                        // relies on filter ordering nobody explicitly guarantees - permit
                        // OPTIONS outright so a preflight can never be rejected as
                        // unauthenticated even if something upstream changes.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/ping", "/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    /**
     * The frontend (Phase 12, a separate Vite dev server) calls this API
     * cross-origin - without this, every browser request from it fails at
     * the preflight before Spring Security ever sees an Authorization
     * header. Origins are listed explicitly (not "*") because
     * allowCredentials(true) requires it.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of(
                "http://localhost:5173",
                "http://127.0.0.1:5173",
                "http://localhost:3000",
                // LAN access for real-device testing (e.g. the mobile punch flow's camera/geolocation,
                // which needs a genuine phone browser, not just responsive-mode devtools) - a wildcard
                // pattern for the whole home/office subnet rather than one hardcoded dev machine IP, since
                // that IP changes across networks/DHCP leases. HTTPS only: mixed content would block API
                // calls from an HTTPS frontend page anyway once server.ssl.enabled is turned on for this.
                "https://192.168.31.*:5173"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With"));
        // Without this, the browser JS can't read these response headers cross-origin -
        // Location matters for every 201 Created (e.g. POST /api/attendance/punch).
        configuration.setExposedHeaders(List.of("Location", "Authorization"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new JwtRoleConverter());
        return converter;
    }

    /**
     * No IdP (Keycloak/Auth0/etc.) is provisioned for any environment yet, so
     * issuer-uri is blank by default everywhere including prod-shaped configs.
     * When blank, this falls back to a symmetric HS256 decoder keyed by
     * security.jwt.local-dev-secret - fine for local development, CI, and this
     * app's own test suite (which mints its own tokens), but explicitly NOT a
     * production posture. Wire a real issuer-uri before any real deployment.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        if (issuerUri != null && !issuerUri.isBlank()) {
            return JwtDecoders.fromIssuerLocation(issuerUri);
        }
        SecretKeySpec key = new SecretKeySpec(localDevSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).build();
    }
}

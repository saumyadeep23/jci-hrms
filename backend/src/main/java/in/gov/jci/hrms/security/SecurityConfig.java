package in.gov.jci.hrms.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
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
    private final String authMode;
    private final Environment environment;

    public SecurityConfig(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String issuerUri,
                           @Value("${security.jwt.local-dev-secret}") String localDevSecret,
                           @Value("${security.auth.mode:local-dev}") String authMode,
                           Environment environment) {
        this.issuerUri = issuerUri;
        this.localDevSecret = localDevSecret;
        this.authMode = authMode;
        this.environment = environment;
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
                        // ONBOARDING_IMPLEMENTATION.md: pre-auth activation endpoint - there is no
                        // authenticated principal yet at this point in the onboarding flow by
                        // design. Token validity/expiry/single-use is enforced entirely inside
                        // UserOnboardingService.activate(), not by authentication.
                        .requestMatchers(HttpMethod.POST, "/api/public/activation").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    /**
     * NETWORK/TLS closure (docs/DEPLOYMENT.md): normal browser traffic no longer needs this. The
     * frontend's apiClient requests a relative '/api/...' (frontend/src/api/client.ts) which, in dev, is
     * proxied server-side by Vite (frontend/vite.config.ts's server.proxy) to this backend - a Node-to-
     * Node HTTP call, never a browser fetch, so it is not subject to CORS at all; in production it is
     * same-origin behind the reverse proxy. This bean remains as a fallback for anyone who bypasses the
     * proxy and calls this API directly from browser JS on a different origin (e.g. ad-hoc testing
     * against a raw http://localhost:8080 target) - it is no longer load-bearing for the primary dev/
     * prod request path, so a LAN IP outside the two wildcarded subnets below no longer breaks normal
     * usage the way it used to. Origins are listed explicitly (not "*") because allowCredentials(true)
     * requires it.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of(
                "http://localhost:5173",
                "http://127.0.0.1:5173",
                "https://localhost:5173",
                "https://127.0.0.1:5173",
                "http://localhost:3000",
                // Fallback only (see class javadoc above) for direct, non-proxied LAN access - a wildcard
                // pattern for the whole home/office subnet rather than one hardcoded dev machine IP, since
                // that IP changes across networks/DHCP leases. HTTPS only: mixed content would block API
                // calls from an HTTPS frontend page anyway once server.ssl.enabled is turned on for this.
                // Add this machine's own subnet here only if you have a specific reason to call this API
                // directly instead of through the Vite proxy - the proxy path (the default, recommended
                // flow) does not need an entry here at all, on any subnet.
                "https://192.168.31.*:5173",
                "https://192.168.137.*:5173"));
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
     * SEC-001 fail-closed remediation (docs/security/SEC_001_002_REMEDIATION.md):
     * which decoder gets built is now a positive, explicit choice
     * (security.auth.mode=oidc|local-dev) rather than being inferred from
     * issuer-uri being blank - and {@link AuthenticationModeGuard} refuses to
     * let a production-like Spring profile (staging/prod - see
     * AuthenticationModeGuard.PRODUCTION_LIKE_PROFILES) end up on local-dev
     * HS256 auth or a missing/default configuration, throwing
     * IllegalStateException to abort application startup instead. No IdP
     * (Keycloak/Auth0/etc.) is provisioned for any environment yet, so
     * security.auth.mode defaults to local-dev and issuer-uri defaults to
     * blank - fine for local development, CI, and this app's own test suite
     * (which mints its own tokens), but this guard is what stops that
     * default from ever reaching a real deployment silently.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        JwtAuthMode mode = JwtAuthMode.parse(authMode);
        boolean productionLike = AuthenticationModeGuard.isProductionLike(environment.getActiveProfiles());
        AuthenticationModeGuard.validate(mode, productionLike, issuerUri, localDevSecret);

        if (mode == JwtAuthMode.OIDC) {
            return JwtDecoders.fromIssuerLocation(issuerUri);
        }
        SecretKeySpec key = new SecretKeySpec(localDevSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).build();
    }
}

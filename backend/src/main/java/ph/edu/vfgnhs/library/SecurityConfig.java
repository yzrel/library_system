package ph.edu.vfgnhs.library;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${library.cors-origins}") String origins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(origins.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of(HttpHeaders.CONTENT_DISPOSITION));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtFilter jwtFilter, AuthRateLimitFilter rateLimitFilter) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> {
                    res.setStatus(401); res.setContentType("application/json");
                    res.getWriter().write("{\"message\":\"Authentication required\"}");
                }))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/health", "/api/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/classes").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/opac/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/attendance/suggest", "/api/attendance/today").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/attendance/check-in", "/api/attendance/feedback").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitFilter, JwtFilter.class)
                .build();
    }
}

@Component
class AuthRateLimitFilter extends OncePerRequestFilter {
    private static final int AUTH_LIMIT = 20;
    private static final int KIOSK_LIMIT = 120;
    private static final long WINDOW_SECONDS = 300;
    private final ConcurrentHashMap<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        if ("GET".equals(method) && (path.equals("/api/attendance/suggest") || path.equals("/api/attendance/today"))) return false;
        return !"POST".equals(method) ||
                !(path.equals("/api/auth/login") || path.equals("/api/auth/register") ||
                        path.equals("/api/attendance/check-in") || path.equals("/api/attendance/feedback"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded == null ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
        String key = ip + ":" + request.getRequestURI();
        Instant cutoff = Instant.now().minusSeconds(WINDOW_SECONDS);
        Deque<Instant> bucket = attempts.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (bucket) {
            while (!bucket.isEmpty() && bucket.peekFirst().isBefore(cutoff)) bucket.removeFirst();
            int limit = request.getRequestURI().startsWith("/api/attendance/") ? KIOSK_LIMIT : AUTH_LIMIT;
            if (bucket.size() >= limit) {
                response.setStatus(429);
                response.setHeader("Retry-After", String.valueOf(WINDOW_SECONDS));
                response.setContentType("application/json");
                response.getWriter().write("{\"message\":\"Too many attempts. Please try again later.\",\"status\":429}");
                return;
            }
            bucket.addLast(Instant.now());
        }
        chain.doFilter(request, response);
    }
}

@Component
class JwtService {
    private final SecretKey key;
    private final long hours;

    JwtService(@Value("${library.jwt-secret}") String secret, @Value("${library.jwt-hours}") long hours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.hours = hours;
    }

    String issue(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.email)
                .claim("role", user.role.name())
                .claim("uid", user.id)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(hours, ChronoUnit.HOURS)))
                .signWith(key)
                .compact();
    }

    Long userId(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().get("uid", Long.class);
    }
}

@Component
class JwtFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final UserRepository users;

    JwtFilter(JwtService jwt, UserRepository users) {
        this.jwt = jwt;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ") && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Long userId = jwt.userId(header.substring(7));
                if (userId == null) throw new IllegalArgumentException("Token has no user id");
                users.findById(userId)
                        .filter(u -> u.status == AccountStatus.ACTIVE)
                        .ifPresent(user -> SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken(
                                        user.email, null,
                                        List.of(new SimpleGrantedAuthority("ROLE_" + user.role.name())))));
            } catch (RuntimeException ignored) {
                // Invalid/expired tokens remain unauthenticated.
            }
        }
        chain.doFilter(request, response);
    }
}

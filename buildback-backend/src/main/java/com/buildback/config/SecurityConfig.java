package com.buildback.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.savedrequest.NullRequestCache;

import com.buildback.dto.ApiError;
import com.buildback.security.AppUserDetails;
import com.buildback.security.JsonResponseWriter;

/**
 * All security rules for the BuildBack API in one place.
 *
 * Login style: classic server-side HTTP session.
 *  - The browser gets an HttpOnly JSESSIONID cookie (JavaScript cannot read it).
 *  - The logged-in user is stored in that session on the server.
 *  - Every state-changing request must also carry a CSRF token in a header.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** BCrypt: slow, salted one-way hashing. The same password gives a different hash each time. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Where the logged-in user is remembered between requests: the HTTP session. */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            SecurityContextRepository securityContextRepository,
            JsonResponseWriter json) throws Exception {

        http
            // CORS rules come from the CorsConfigurationSource bean in CorsConfig
            .cors(Customizer.withDefaults())

            // CSRF stays ON. The token lives in the server session; React asks for it
            // at GET /api/auth/csrf and sends it back in the X-CSRF-TOKEN header.
            // The XOR handler masks the token differently on every response (BREACH protection).
            .csrf(csrf -> csrf
                .csrfTokenRepository(new HttpSessionCsrfTokenRepository())
                .csrfTokenRequestHandler(new XorCsrfTokenRequestAttributeHandler()))

            // Save the authenticated user into the HTTP session after login
            .securityContext(context -> context.securityContextRepository(securityContextRepository))

            .sessionManagement(session -> session
                // New session ID after login, so a session ID planted before login is useless
                .sessionFixation(fixation -> fixation.changeSessionId()))

            // Who may call what. Anything not listed is denied.
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/api/health", "/api/auth/csrf").permitAll()
                // Public catalogue is read-only: only GET is open to everyone
                .requestMatchers(HttpMethod.GET, "/api/materials", "/api/materials/*",
                        "/api/materials/*/tile-estimate").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/auth/me").authenticated()
                .requestMatchers("/api/buyer/**").hasRole("BUYER") // orders and payments
                // /api/seller/** also covers /api/seller/orders; /api/admin/** covers moderation
                .requestMatchers("/api/seller/**").hasRole("SELLER") // includes /api/seller/materials CRUD
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/error").permitAll()
                .anyRequest().denyAll())

            // Spring Security's own login filter handles POST /api/auth/login
            // (form fields "email" and "password") and answers with JSON, not redirects.
            .formLogin(form -> form
                .loginPage("/api/auth/login") // also turns off Spring's generated HTML login page
                .loginProcessingUrl("/api/auth/login")
                .usernameParameter("email")
                .passwordParameter("password")
                .successHandler((request, response, authentication) -> {
                    AppUserDetails user = (AppUserDetails) authentication.getPrincipal();
                    json.write(response, HttpStatus.OK.value(), user.toResponse());
                })
                .failureHandler((request, response, exception) ->
                    // Same message for unknown email and wrong password
                    json.write(response, HttpStatus.UNAUTHORIZED.value(),
                            ApiError.of("Invalid email or password")))
                .permitAll())

            // POST /api/auth/logout: invalidate session, clear user and CSRF token, delete cookie
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID")
                .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))

            // JSON instead of redirects / browser pop-ups
            .httpBasic(AbstractHttpConfigurer::disable)
            .requestCache(cache -> cache.requestCache(new NullRequestCache()))
            .exceptionHandling(errors -> errors
                // Not logged in -> 401
                .authenticationEntryPoint((request, response, exception) ->
                    json.write(response, HttpStatus.UNAUTHORIZED.value(),
                            ApiError.of("Please log in to continue")))
                // Logged in but wrong role, or missing/invalid CSRF token -> 403
                .accessDeniedHandler((request, response, exception) -> {
                    String message = exception instanceof CsrfException
                            ? "Security token missing or expired. Please try again."
                            : "You do not have permission to access this page";
                    json.write(response, HttpStatus.FORBIDDEN.value(), ApiError.of(message));
                }));

        return http.build();
    }
}

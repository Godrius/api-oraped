package br.com.oraped.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.CommonsRequestLoggingFilter;

import br.com.oraped.security.SecurityFilter;
import lombok.RequiredArgsConstructor;

@Configuration
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

  private final SecurityFilter securityFilter;
  private final CorsConfigurationSource corsConfigurationSource;

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http
      .cors(cors -> cors.configurationSource(corsConfigurationSource))
      .csrf(csrf -> csrf.disable())
      .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .authorizeHttpRequests(auth -> auth
        .requestMatchers("/health").permitAll()
        .requestMatchers("/error").permitAll()
        .requestMatchers(
    		  "/v3/api-docs/**",
    		  "/swagger-ui/**",
    		  "/swagger-ui.html",
    		  "/api/v3/api-docs/**",
    		  "/api/swagger-ui/**",
    		  "/api/swagger-ui.html"
    		).permitAll()
        
        // Actuator
        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()

        // Auth
        .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
        .requestMatchers(HttpMethod.POST, "/auth/login-por-token").permitAll()

        // Rotas públicas do produto
        .requestMatchers(HttpMethod.POST, "/pedidos").permitAll()
        .requestMatchers(HttpMethod.GET, "/whatsapp").permitAll()
        .requestMatchers(HttpMethod.POST, "/whatsapp").permitAll()
        
        .anyRequest().authenticated()
      )
      .addFilterBefore(securityFilter, UsernamePasswordAuthenticationFilter.class)
      .exceptionHandling(e -> e
        .authenticationEntryPoint((req, res, ex) -> res.sendError(401))
        .accessDeniedHandler((req, res, ex) -> res.sendError(403))
      )
      .build();
  }

  @Bean
  public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
    return config.getAuthenticationManager();
  }

  @Bean
  public BCryptPasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public FilterRegistrationBean<CommonsRequestLoggingFilter> logFilter() {
    CommonsRequestLoggingFilter filter = new CommonsRequestLoggingFilter();
    filter.setIncludeQueryString(true);
    filter.setIncludePayload(true);
    filter.setIncludeHeaders(true);
    filter.setMaxPayloadLength(10000);

    FilterRegistrationBean<CommonsRequestLoggingFilter> registration = new FilterRegistrationBean<>();
    registration.setFilter(filter);
    return registration;
  }
}

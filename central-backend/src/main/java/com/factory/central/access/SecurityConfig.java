package com.factory.central.access;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private final AuthProperties authProperties;
  private final AuthSessionRepository sessionRepository;
  private final ActorRepository actorRepository;
  private final EdgeSiteCredentialRepository credentialRepository;
  private final ActorUserDetailsService userDetailsService;
  private final PasswordEncoder passwordEncoder;
  private final Clock clock;

  public SecurityConfig(
      AuthProperties authProperties,
      AuthSessionRepository sessionRepository,
      ActorRepository actorRepository,
      EdgeSiteCredentialRepository credentialRepository,
      ActorUserDetailsService userDetailsService,
      PasswordEncoder passwordEncoder,
      Clock clock) {
    this.authProperties = authProperties;
    this.sessionRepository = sessionRepository;
    this.actorRepository = actorRepository;
    this.credentialRepository = credentialRepository;
    this.userDetailsService = userDetailsService;
    this.passwordEncoder = passwordEncoder;
    this.clock = clock;
  }

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    SessionCookieAuthenticationFilter sessionFilter =
        new SessionCookieAuthenticationFilter(
            authProperties, sessionRepository, actorRepository, clock);
    EdgeTokenAuthenticationFilter edgeFilter = new EdgeTokenAuthenticationFilter(credentialRepository);
    http.csrf(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .headers(headers -> headers.cacheControl(cache -> {}))
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
        .addFilterBefore(edgeFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(sessionFilter, EdgeTokenAuthenticationFilter.class);
    return http.build();
  }

  @Bean
  AuthenticationManager authenticationManager() {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
    provider.setUserDetailsService(userDetailsService);
    provider.setPasswordEncoder(passwordEncoder);
    provider.setHideUserNotFoundExceptions(true);
    return new ProviderManager(provider);
  }
}
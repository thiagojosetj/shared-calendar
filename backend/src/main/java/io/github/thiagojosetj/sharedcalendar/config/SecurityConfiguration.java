package io.github.thiagojosetj.sharedcalendar.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuração de segurança da fundação (Fase 0).
 *
 * <p>Neste momento a aplicação ainda não tem autenticação: o objetivo aqui é apenas garantir que o
 * esqueleto não suba com o Actuator aberto. O único recurso público é o health check.
 *
 * <p>A estratégia definitiva — sessão server-side com cookie HttpOnly, proteção CSRF por cookie e
 * login com Google — está decidida no ADR-0003 e é implementada na Fase 1.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                // Sem login por formulário nem HTTP Basic: um SPA não usa nenhum dos dois, e deixar
                // o padrão ligado geraria a senha aleatória no log de inicialização.
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable());
        return http.build();
    }
}

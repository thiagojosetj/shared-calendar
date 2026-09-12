package io.github.thiagojosetj.sharedcalendar.config;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

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
                        // Quando uma requisição é recusada (por exemplo, 403 por token CSRF ausente), o
                        // Tomcat faz um "error dispatch" interno para /error, para renderizar a resposta.
                        // Se esse dispatch também exigisse autenticação, o visitante anônimo cairia no
                        // entry point e o 403 real seria reescrito como 401, além de criar uma sessão
                        // só para guardar a URL /error. Liberar o dispatch de erro não expõe nenhum
                        // recurso: a requisição original já foi negada, o dispatch só descreve o erro.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                // Sem login por formulário nem HTTP Basic: um SPA não usa nenhum dos dois.
                // A linha "Using generated security password" que ainda aparece no log vem do usuário
                // em memória que o Spring Boot cria enquanto não existe um UserDetailsService próprio.
                // Com form e Basic desligados ele não serve para autenticar nada, e some na Fase 1.
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                // Sem um mecanismo de login ligado, o Spring Security responderia 403 a quem não está
                // autenticado. O correto é 401: "não autenticado" é diferente de "autenticado sem
                // permissão", e o frontend depende dessa diferença para redirecionar ao login (ADR-0009).
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        return http.build();
    }
}

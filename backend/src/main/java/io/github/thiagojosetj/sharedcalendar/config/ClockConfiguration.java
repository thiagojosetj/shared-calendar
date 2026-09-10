package io.github.thiagojosetj.sharedcalendar.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Disponibiliza um {@link Clock} injetável para toda a aplicação.
 *
 * <p>Nenhum código de domínio deve chamar {@code Instant.now()}, {@code LocalDate.now()} ou
 * {@code System.currentTimeMillis()} diretamente. Sem isso, testar "o evento sai da lixeira depois
 * de 72 horas" ou "o lembrete dispara 1 hora antes" exigiria esperar ou mexer no relógio da máquina;
 * com um {@code Clock} injetado, cada caso vira um teste determinístico de milissegundos (RN-TZ-08).
 *
 * <p>O relógio é fixado em UTC de propósito. O fuso do usuário é um atributo do perfil e nunca é
 * inferido do fuso do servidor (RN-TZ-01, ADR-0002).
 */
@Configuration
public class ClockConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}

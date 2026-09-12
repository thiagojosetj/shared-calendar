package io.github.thiagojosetj.sharedcalendar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * Ponto de entrada da aplicação.
 *
 * <p>{@link UserDetailsServiceAutoConfiguration} é excluída porque, enquanto não existe um
 * {@code UserDetailsService} próprio, o Spring Boot cria um usuário em memória e imprime a senha dele
 * no log. Senhas não devem aparecer em log (RN-SEC-09), mesmo uma senha descartável. Na Fase 1, quando a
 * autenticação real existir, essa auto-configuração já recuaria sozinha e a exclusão pode ser removida.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class SharedCalendarApplication {

    public static void main(String[] args) {
        SpringApplication.run(SharedCalendarApplication.class, args);
    }
}

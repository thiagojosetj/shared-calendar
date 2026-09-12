package io.github.thiagojosetj.sharedcalendar;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * PostgreSQL real para os testes de integração.
 *
 * <p>Os testes rodam contra a mesma imagem do {@code docker-compose.yml}, e não contra um banco em
 * memória. Um dialeto diferente não provaria nada sobre índices parciais, {@code timestamptz} ou
 * constraints específicas do PostgreSQL, que são as partes arriscadas deste schema (ADR-0001). A
 * versão também importa: a imagem 18 mudou a raiz dos dados em relação às anteriores.
 *
 * <p>{@link ServiceConnection} faz o Spring Boot ler host, porta, usuário e senha diretamente do
 * container e configurar o datasource com eles, sem propriedades copiadas à mão.
 *
 * <p>O container é um bean: ele sobe junto com o contexto de teste e é reaproveitado enquanto o
 * Spring reutilizar esse contexto em cache. Classes de teste com a mesma configuração compartilham,
 * portanto, um único container por execução da suíte.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /** Mantenha igual à imagem do {@code docker-compose.yml} da raiz do projeto. */
    static final String POSTGRES_IMAGE = "postgres:18-alpine";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE));
    }
}

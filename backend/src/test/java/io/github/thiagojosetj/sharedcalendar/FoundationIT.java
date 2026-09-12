package io.github.thiagojosetj.sharedcalendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Prova que a fundação funciona de ponta a ponta contra um PostgreSQL real (F0-06).
 *
 * <p>Automatiza as verificações que antes eram feitas à mão com {@code psql} e {@code curl}: a
 * aplicação sobe, o Flyway aplica as migrations, o {@code ddl-auto=validate} aceita o schema, o health
 * check é público e todo o resto exige autenticação.
 *
 * <p><strong>Por que requisições HTTP reais, e não MockMvc.</strong> O MockMvc não passa por um
 * container de servlets, então não existe o "error dispatch" que o Tomcat faz para {@code /error}
 * quando uma requisição é recusada. Foi justamente esse dispatch que, sem proteção adequada, reescrevia
 * um 403 de CSRF como 401. Com MockMvc esse defeito voltaria sem nenhum teste falhar. Por isso a
 * aplicação sobe em porta aleatória e o teste faz o mesmo que o {@code curl} fazia.
 *
 * <p>O sufixo {@code IT} faz esta classe rodar pelo Failsafe em {@code ./mvnw verify}, e não pelo
 * Surefire em {@code ./mvnw test}. Assim os testes unitários continuam rodando sem Docker.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class FoundationIT {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("o Flyway aplica todas as migrations com sucesso")
    void aplicaTodasAsMigrations() {
        List<Map<String, Object>> migrations = jdbcTemplate.queryForList("""
                select version, script, success
                  from flyway_schema_history
                 where version is not null
                 order by installed_rank
                """);

        assertThat(migrations)
                .as("migrations registradas no flyway_schema_history")
                .extracting(row -> row.get("script"))
                .contains("V1__create_app_user.sql");
        assertThat(migrations)
                .as("nenhuma migration pode ter falhado")
                .allSatisfy(row -> assertThat(row.get("success")).isEqualTo(true));
    }

    @Test
    @DisplayName("a V1 cria a tabela app_user no schema public")
    void criaTabelaAppUser() {
        String tabela = jdbcTemplate.queryForObject(
                "select to_regclass('public.app_user')::text", String.class);

        assertThat(tabela).isEqualTo("app_user");
    }

    @Test
    @DisplayName("o health check é público, responde UP e não expõe detalhes a anônimos")
    void healthCheckPublico() throws Exception {
        HttpResponse<String> resposta = enviar(HttpRequest.newBuilder(url("/actuator/health")).GET());

        assertThat(resposta.statusCode()).isEqualTo(200);
        assertNaoCriaSessao(resposta);
        JsonNode corpo = JSON.readTree(resposta.body());
        assertThat(corpo.path("status").asString()).isEqualTo("UP");
        // show-details: when-authorized. Um visitante anônimo não pode ver quais componentes
        // existem nem detalhes da infraestrutura (RN-SEC-05).
        assertThat(corpo.has("components")).as("detalhes do health para anônimo").isFalse();
    }

    @ParameterizedTest(name = "GET {0} → 401")
    @ValueSource(strings = {"/", "/api/v1/qualquer-coisa", "/actuator/env"})
    @DisplayName("leitura sem autenticação responde 401")
    void leituraSemAutenticacao(String caminho) throws Exception {
        HttpResponse<String> resposta = enviar(HttpRequest.newBuilder(url(caminho)).GET());

        // 401 e não 403: "não autenticado" é diferente de "sem permissão", e o frontend depende
        // dessa diferença para redirecionar ao login (ADR-0009).
        assertThat(resposta.statusCode()).isEqualTo(401);
        // O request cache criava uma sessão para cada 401 anônimo.
        assertNaoCriaSessao(resposta);
    }

    @Test
    @DisplayName("escrita sem token CSRF é recusada com 403, sem ser mascarada como 401")
    void escritaSemTokenCsrf() throws Exception {
        HttpResponse<String> resposta = enviar(HttpRequest.newBuilder(url("/api/v1/qualquer-coisa"))
                .POST(HttpRequest.BodyPublishers.noBody()));

        // O filtro de CSRF roda antes da autorização e recusa a requisição com 403. Se o error dispatch
        // para /error voltar a exigir autenticação, este status vira 401 e o teste falha.
        assertThat(resposta.statusCode()).isEqualTo(403);
        // O repositório de CSRF padrão guardava o token na sessão, criando uma sessão por POST anônimo.
        assertNaoCriaSessao(resposta);
    }

    /**
     * Visitantes anônimos não devem criar sessão HTTP. Na Fase 1 a sessão fica no PostgreSQL (ADR-0003),
     * e cada requisição anônima que criasse sessão viraria uma linha no banco.
     */
    private static void assertNaoCriaSessao(HttpResponse<String> resposta) {
        assertThat(resposta.headers().allValues("Set-Cookie"))
                .as("cookies emitidos para um visitante anônimo")
                .noneMatch(cookie -> cookie.startsWith("JSESSIONID="));
    }

    private URI url(String caminho) {
        return URI.create("http://localhost:" + port + caminho);
    }

    private static HttpResponse<String> enviar(HttpRequest.Builder requisicao)
            throws IOException, InterruptedException {
        return HTTP.send(requisicao.build(), HttpResponse.BodyHandlers.ofString());
    }
}

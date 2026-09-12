package io.github.thiagojosetj.sharedcalendar.shared.error;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.thiagojosetj.sharedcalendar.config.SecurityConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Garante que erros saem no formato {@code application/problem+json} e sem vazar detalhe interno
 * (RN-SEC-05).
 *
 * <p>{@link WebMvcTest} sobe apenas a camada web — controllers, {@code @ControllerAdvice} e filtros —,
 * sem banco. Por isso este teste roda em {@code ./mvnw test}, sem Docker. A configuração de segurança é
 * importada explicitamente porque a fatia web não a inclui sozinha, e o teste precisa dela para provar
 * que 401 e 403 continuam sendo decididos pelo Spring Security.
 */
@WebMvcTest(controllers = GlobalExceptionHandlerTest.ControllerDeTeste.class)
@Import({SecurityConfiguration.class, GlobalExceptionHandlerTest.ControllerDeTeste.class})
@ActiveProfiles("test")
@WithMockUser
class GlobalExceptionHandlerTest {

    /** Texto que simula informação interna e que nunca pode chegar ao cliente. */
    private static final String DETALHE_INTERNO = "falha ao ler a coluna password_hash da tabela app_user";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("erro inesperado vira 500 em problem+json, sem mensagem interna nem stack trace")
    void erroInesperado() throws Exception {
        mockMvc.perform(get("/teste/erro-inesperado"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.title").value("Erro interno"))
                .andExpect(content().string(not(containsString("password_hash"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(content().string(not(containsString("at io.github"))));
    }

    @Test
    @DisplayName("parâmetro com tipo inválido vira 400 em problem+json")
    void parametroInvalido() throws Exception {
        mockMvc.perform(get("/teste/parametro").param("numero", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("rota inexistente vira 404 em problem+json")
    void rotaInexistente() throws Exception {
        mockMvc.perform(get("/teste/rota-que-nao-existe"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("negação de acesso continua sendo 403, e não é convertida em 500")
    void acessoNegadoNaoViraErroInterno() throws Exception {
        // O tratador genérico captura Exception. Sem o cuidado de relançar as exceções do Spring Security,
        // uma negação de acesso lançada dentro de um controller viraria "Erro interno".
        mockMvc.perform(get("/teste/acesso-negado"))
                .andExpect(status().isForbidden());
    }

    @RestController
    static class ControllerDeTeste {

        @GetMapping("/teste/erro-inesperado")
        String erroInesperado() {
            throw new IllegalStateException(DETALHE_INTERNO);
        }

        @GetMapping("/teste/parametro")
        String parametro(@RequestParam int numero) {
            return "recebido " + numero;
        }

        @GetMapping("/teste/acesso-negado")
        String acessoNegado() {
            throw new AccessDeniedException("sem permissão");
        }
    }
}

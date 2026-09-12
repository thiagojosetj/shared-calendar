package io.github.thiagojosetj.sharedcalendar.shared.error;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Ponto único de tradução de exceções para respostas HTTP.
 *
 * <p>Estender {@link ResponseEntityExceptionHandler} faz as exceções do próprio Spring MVC (payload
 * inválido, parâmetro com tipo errado, rota inexistente, método não suportado) serem devolvidas como
 * {@code application/problem+json}, no formato da RFC 9457.
 *
 * <p>Nenhuma resposta daqui pode conter stack trace, SQL, nome de tabela ou mensagem interna de exceção
 * (RN-SEC-05). O detalhe técnico vai para o log do servidor, e o cliente recebe apenas uma mensagem
 * genérica.
 *
 * <p>Os tratadores das exceções de domínio (não encontrado, sem permissão, conflito de regra) são
 * acrescentados aqui conforme cada domínio for implementado. A distinção entre 403 e 404, que é decisão
 * de segurança e não de estilo (RN-AUTZ-32, ADR-0004), fica neste arquivo e em nenhum outro.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Última linha de defesa para exceções que nenhum outro tratador reconheceu.
     *
     * <p>Exceções de autenticação e autorização do Spring Security são relançadas, e não convertidas em
     * 500: quem decide 401 e 403 para elas é o próprio Spring Security.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception exception, HttpServletRequest request) throws Exception {
        if (exception instanceof AuthenticationException || exception instanceof AccessDeniedException) {
            throw exception;
        }

        LOG.error("Erro inesperado em {} {}", request.getMethod(), request.getRequestURI(), exception);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocorreu um erro inesperado. Tente novamente mais tarde.");
        problem.setTitle("Erro interno");
        return problem;
    }
}

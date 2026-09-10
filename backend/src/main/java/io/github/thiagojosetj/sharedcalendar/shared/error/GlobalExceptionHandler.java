package io.github.thiagojosetj.sharedcalendar.shared.error;

import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Ponto único de tradução de exceções para respostas HTTP.
 *
 * <p>Estender {@link ResponseEntityExceptionHandler} faz as exceções do próprio Spring MVC
 * (payload inválido, método não suportado, parâmetro ausente, falha de validação) serem devolvidas
 * como {@code application/problem+json}, no formato da RFC 9457, em vez do corpo de erro padrão.
 *
 * <p>Nenhuma resposta daqui pode conter stack trace, SQL ou nome de tabela (RN-SEC-05). A
 * configuração {@code server.error.include-*: never} garante isso também para o caminho de erro
 * que não passa por este handler.
 *
 * <p>Os tratadores das exceções de domínio (não encontrado, sem permissão, conflito de regra) são
 * acrescentados aqui conforme cada domínio for implementado. A distinção entre 403 e 404 — que é
 * decisão de segurança, não de estilo (RN-AUTZ-32, ADR-0004) — mora neste arquivo, e em nenhum outro.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
}

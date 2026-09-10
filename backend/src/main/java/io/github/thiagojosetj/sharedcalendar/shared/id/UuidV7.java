package io.github.thiagojosetj.sharedcalendar.shared.id;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;

/**
 * Gerador de identificadores UUID versão 7, conforme a RFC 9562.
 *
 * <p>Um UUID v7 carrega, nos 48 bits mais significativos, o instante Unix em milissegundos. Isso o
 * torna ordenável no tempo, o que preserva a localidade de inserção no índice B-tree do PostgreSQL —
 * a fragmentação que um UUID v4 aleatório causaria conforme a tabela cresce. Ao mesmo tempo, os 74
 * bits aleatórios restantes tornam o identificador não adivinhável, o que impede a enumeração de
 * recursos pelas URLs da API (RN-AUTZ-32).
 *
 * <p>A alternativa seria uma dependência externa apenas para gerar um identificador. O layout é
 * curto e está inteiramente especificado, então implementá-lo aqui custa menos que mantê-la.
 *
 * <p>A ordenação é garantida na granularidade de <strong>milissegundo</strong>. Dois identificadores
 * gerados no mesmo milissegundo não têm ordem definida entre si — o que é suficiente para o objetivo
 * de localidade no índice, e é a razão de não haver sincronização aqui.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9562#name-uuid-version-7">RFC 9562, seção 5.7</a>
 */
public final class UuidV7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    /** Máscara dos 48 bits do timestamp. */
    private static final long TIMESTAMP_MASK = 0xFFFF_FFFF_FFFFL;

    /** Versão 7 posicionada nos bits 15..12 da metade mais significativa. */
    private static final long VERSION_7 = 0x7000L;

    /** Variante RFC 9562 ("10") posicionada nos bits 63..62 da metade menos significativa. */
    private static final long VARIANT_RFC = 0x8000_0000_0000_0000L;

    private static final long VARIANT_CLEAR_MASK = 0x3FFF_FFFF_FFFF_FFFFL;

    private UuidV7() {
        // utilitário
    }

    /**
     * Gera um novo identificador usando o relógio informado.
     *
     * <p>O {@link Clock} é parâmetro, e não {@code System.currentTimeMillis()}, porque toda regra
     * que depende de "agora" neste projeto precisa ser determinística em teste (RN-TZ-08).
     */
    public static UUID generate(Clock clock) {
        long timestamp = clock.millis();

        byte[] random = new byte[10];
        RANDOM.nextBytes(random);

        // bits 63..16: timestamp | bits 15..12: versão | bits 11..0: aleatório
        long mostSignificant = (timestamp & TIMESTAMP_MASK) << 16
                | VERSION_7
                | ((long) (random[0] & 0x0F) << 8)
                | (random[1] & 0xFFL);

        // bits 63..62: variante | bits 61..0: aleatório
        long leastSignificant = 0L;
        for (int i = 2; i < random.length; i++) {
            leastSignificant = (leastSignificant << 8) | (random[i] & 0xFFL);
        }
        leastSignificant = (leastSignificant & VARIANT_CLEAR_MASK) | VARIANT_RFC;

        return new UUID(mostSignificant, leastSignificant);
    }

    /**
     * Extrai o instante embutido no identificador, em milissegundos desde a época Unix.
     *
     * <p>Existe para os testes e para depuração. Não deve ser usado como substituto de uma coluna
     * {@code created_at}: o identificador é um detalhe técnico, não um dado de negócio.
     */
    public static long timestampMillis(UUID uuid) {
        if (uuid.version() != 7) {
            throw new IllegalArgumentException("Não é um UUID versão 7: " + uuid);
        }
        return uuid.getMostSignificantBits() >>> 16;
    }
}

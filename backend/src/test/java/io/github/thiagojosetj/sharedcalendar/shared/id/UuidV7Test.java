package io.github.thiagojosetj.sharedcalendar.shared.id;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UuidV7Test {

    private static final Instant REFERENCE = Instant.parse("2026-09-09T12:00:00Z");

    @Test
    @DisplayName("gera identificador na versão 7 e com a variante da RFC 9562")
    void geraVersaoEVarianteCorretas() {
        UUID id = UuidV7.generate(Clock.fixed(REFERENCE, ZoneOffset.UTC));

        assertThat(id.version()).isEqualTo(7);
        // Variante "10" da RFC 9562 é reportada como 2 por java.util.UUID.
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    @DisplayName("embute o instante do relógio informado, e não o relógio do sistema")
    void embuteOInstanteDoRelogio() {
        UUID id = UuidV7.generate(Clock.fixed(REFERENCE, ZoneOffset.UTC));

        assertThat(UuidV7.timestampMillis(id)).isEqualTo(REFERENCE.toEpochMilli());
    }

    @Test
    @DisplayName("ordena lexicograficamente na mesma ordem do tempo")
    void ordenaNaMesmaOrdemDoTempo() {
        // A localidade de inserção no índice B-tree depende disso (ADR-0001).
        List<String> gerados = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            Instant instant = REFERENCE.plusMillis(i);
            gerados.add(UuidV7.generate(Clock.fixed(instant, ZoneOffset.UTC)).toString());
        }

        assertThat(gerados).isSorted();
    }

    @Test
    @DisplayName("não repete identificadores gerados no mesmo instante")
    void naoRepeteNoMesmoInstante() {
        Clock parado = Clock.fixed(REFERENCE, ZoneOffset.UTC);

        Set<UUID> gerados = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            gerados.add(UuidV7.generate(parado));
        }

        assertThat(gerados).hasSize(10_000);
    }

    @Test
    @DisplayName("rejeita extrair timestamp de um UUID que não é versão 7")
    void rejeitaTimestampDeOutraVersao() {
        UUID v4 = UUID.randomUUID();

        assertThat(v4.version()).isEqualTo(4);
        assertThat(org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> UuidV7.timestampMillis(v4)))
                .hasMessageContaining("versão 7");
    }
}

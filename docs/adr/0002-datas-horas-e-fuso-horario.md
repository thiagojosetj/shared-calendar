# ADR-0002 — Datas, horas e fuso horário

- **Status:** Aceito
- **Data:** 2026-09-09
- **Regras atendidas:** `RN-TZ-01` a `RN-TZ-08`, `RN-EVT-20` a `RN-EVT-22`, `RN-AVL-05`, `RN-AVL-08`

## Contexto

Em um calendário, tempo não é um detalhe de implementação: é o domínio. As decisões erradas aqui produzem
bugs que só aparecem em outubro, em um fuso específico, para um usuário específico — o pior tipo de bug.

Três fatos tornam isso não trivial neste produto:

1. **Um evento de dia inteiro não é um instante.** "Feriado no dia 12" acontece no dia 12 para todo mundo,
   em qualquer fuso. Se armazenarmos como instante, ele aparece no dia 11 para quem está a oeste.
2. **Um evento com horário é um instante.** "Reunião às 14h de Brasília" é o mesmo momento para todos; quem
   está em Lisboa vê 18h ou 19h dependendo do horário de verão.
3. **Uma série recorrente não é uma lista de instantes.** "Toda segunda às 09:00" é uma regra em horário
   *civil*. Se um dos fusos envolvidos entrar em horário de verão, o instante muda mas o horário civil
   permanece 09:00. Guardar a série como uma sequência de instantes quebra exatamente aí.

## Decisão

### 1. Três formas de tempo, três representações distintas

| Conceito | Java | PostgreSQL | Exemplo |
|---|---|---|---|
| **Instante absoluto** (evento com horário, `created_at`, `deleted_at`, disparo de lembrete) | `Instant` | `timestamptz` | `2026-09-12T17:00:00Z` |
| **Data civil** (evento de dia inteiro) | `LocalDate` | `date` | `2026-09-12` |
| **Fuso de referência** | `ZoneId` | `varchar(64)` (ID IANA) | `America/Sao_Paulo` |
| **Horário civil de uma regra de recorrência** | `LocalTime` | `time` | `09:00` |

**`LocalDateTime` é proibido em entidades e DTOs.** Ele representa "uma data e hora sem fuso", o que em um
sistema com usuários em fusos diferentes é sempre ambíguo. Onde parece necessário, o que se quer é
`Instant` (momento) ou o par `LocalDate` + `LocalTime` + `ZoneId` (regra civil).

`ZonedDateTime` e `OffsetDateTime` são usados apenas como **valores de passagem**, na conversão para
apresentação. Nunca são persistidos: `OffsetDateTime` guarda um deslocamento (`-03:00`), não um fuso, e
um deslocamento não sabe quando o horário de verão começa.

### 2. Evento com horário

```sql
starts_at   timestamptz NOT NULL,
ends_at     timestamptz NOT NULL,
time_zone   varchar(64) NOT NULL,   -- ID IANA do fuso de referência
all_day     boolean     NOT NULL
```

O instante é a verdade. O `time_zone` é guardado **junto** por três motivos concretos:

1. é a âncora da recorrência (item 4);
2. permite exibir "o horário original" quando isso importa ("a reunião é às 14h **de Brasília**");
3. permite recalcular corretamente quando o evento é movido ou a regra da série muda.

Guardar só o instante perde informação que não pode ser reconstruída.

### 3. Evento de dia inteiro

```sql
start_date        date NOT NULL,
end_date_exclusive date NOT NULL
```

Sem instante e sem fuso. Converter um evento de dia inteiro para `Instant` é considerado **defeito**.

**O fim é exclusivo** — `end_date_exclusive` é o dia *seguinte* ao último dia do evento. Um evento de um dia
em 12/09 tem `start_date = 2026-09-12` e `end_date_exclusive = 2026-09-13`.

Essa escolha é contraintuitiva e merece justificativa. As duas fronteiras externas do sistema usam fim
exclusivo: o `DTEND` de eventos `VALUE=DATE` no RFC 5545 (iCalendar) e a propriedade `end` do FullCalendar.
Se armazenássemos o fim inclusivo, faríamos ±1 dia em cada uma dessas fronteiras — e um `off-by-one` de
data é um bug silencioso e constante. Armazenando exclusivo, a conversão acontece em **um** lugar: a
apresentação para o usuário.

Para que o código nunca leia a coluna crua por engano, o domínio expõe métodos com nome:

```java
LocalDate lastDay()            // end_date_exclusive.minusDays(1) — o que o usuário chama de "termina em"
int durationInDays()
boolean coversDay(LocalDate d)
```

A API expõe o campo com o nome `endDateExclusive`, para que o contrato seja autoexplicativo.

### 4. Recorrência é ancorada em horário civil

Uma série guarda `LocalTime` + `ZoneId`, não uma lista de instantes. A expansão calcula, para cada
ocorrência:

```java
ZonedDateTime zdt = occurrenceDate.atTime(localTime).atZone(referenceZone);
Instant start = zdt.toInstant();
```

`ZoneId.getRules()` aplica o horário de verão automaticamente. Uma reunião semanal das 09:00 em
`America/Sao_Paulo` continua às 09:00 civis mesmo que o país volte a adotar horário de verão — o `Instant`
resultante muda, o horário civil não. É exatamente o comportamento de `RN-TZ-06`.

Dois casos-limite existem e precisam de decisão explícita, porque `atZone()` resolve ambos silenciosamente:

| Caso | O que acontece | Decisão |
|---|---|---|
| Horário **inexistente** (o relógio pula 00:00 → 01:00 e a série é às 00:30) | `atZone()` desloca para frente, para 01:30 | Aceitar o comportamento padrão e **registrar em teste**, para que seja intencional e não acidental |
| Horário **ambíguo** (o relógio volta e as 23:30 acontecem duas vezes) | `atZone()` escolhe o **primeiro** (antes da transição) | Aceitar o padrão e registrar em teste |

### 5. Fuso do usuário

`users.time_zone` é obrigatório, com padrão `America/Sao_Paulo` no cadastro e alterável no perfil. O fuso do
**servidor nunca é usado** como fuso do usuário: a JVM roda com `-Duser.timezone=UTC` e o container também,
justamente para que qualquer código que dependa implicitamente do fuso do sistema quebre em desenvolvimento
e não em produção.

### 6. Configuração que faz isso valer

```properties
spring.jpa.properties.hibernate.jdbc.time_zone=UTC
spring.jackson.datatype.datetime.write-dates-as-timestamps=false
```

- `hibernate.jdbc.time_zone=UTC` garante que o driver JDBC não reinterprete valores usando o fuso da JVM.
- Jackson serializa `Instant` como ISO-8601 com `Z` (`2026-09-12T17:00:00Z`) e `LocalDate` como
  `2026-09-12`. Nunca como número epoch — um número no JSON não diz se é segundo ou milissegundo.

> **Correção (2026-09-12).** A primeira versão deste ADR citava
> `spring.jackson.serialization.write-dates-as-timestamps`, que é a propriedade do Jackson 2. O Spring
> Boot 4 usa Jackson 3, em que a feature foi movida de `SerializationFeature` para `DateTimeFeature`, e a
> propriedade antiga impede a aplicação de subir. A decisão não mudou, apenas o nome da configuração.
> Verificado executando o Jackson 3.1.0: a feature já vem desligada por padrão e a saída é exatamente a
> descrita acima.

### 7. Contrato da API

```jsonc
// evento com horário
{
  "allDay": false,
  "startsAt": "2026-09-12T17:00:00Z",   // instante, sempre UTC, sempre com Z
  "endsAt":   "2026-09-12T18:00:00Z",
  "timeZone": "America/Sao_Paulo"        // fuso de referência do evento
}

// evento de dia inteiro
{
  "allDay": true,
  "startDate": "2026-09-12",
  "endDateExclusive": "2026-09-13"       // dia seguinte ao último dia
}
```

O backend **sempre** envia instantes em UTC. A conversão para o fuso de exibição é feita no frontend, que
sabe qual fuso o usuário escolheu. Isso mantém uma única fonte de verdade e evita que o servidor precise
renderizar a mesma resposta de N formas.

### 8. Relógio injetável

Um bean `Clock` é declarado em `config`. Nenhum código de domínio chama `Instant.now()`, `LocalDate.now()`
ou `System.currentTimeMillis()` diretamente — todos recebem `Clock` e chamam `Instant.now(clock)`.

Sem isso, testar "o evento sai da lixeira depois de 72 horas", "o lembrete dispara 1 hora antes" ou
"a série termina em dezembro" exigiria esperar ou manipular o relógio do sistema. Com `Clock.fixed(...)`,
cada um vira um teste determinístico de milissegundos.

### 9. Consultas por período

Filtros por período usam `timestamptz` com intervalo **semiaberto** `[início, fim)`, para que eventos
adjacentes não sejam contados duas vezes e para que `fim == início` não conte como sobreposição
(`RN-CFL-03`).

Eventos de dia inteiro são consultados por `date`, em uma condição separada da dos eventos com horário — a
consulta de um período tem os dois ramos unidos, não uma conversão de um tipo para o outro.

## Consequências

**Positivas.**
- Cada categoria de tempo tem um tipo que a representa corretamente; erros de categoria viram erro de
  compilação, não bug em produção.
- Horário de verão é tratado pela biblioteca padrão, com o comportamento fixado por testes.
- `Clock` injetável torna testável toda a parte do sistema que depende de "agora" — que aqui é grande.

**Negativas e custos aceitos.**
- Duas representações de evento (com horário e de dia inteiro) significam ramos duplos em consulta,
  validação, detecção de conflito e disponibilidade. É complexidade essencial do domínio, não acidental.
- Fim exclusivo em dia inteiro exige disciplina e bons nomes; mitigado pelos acessores de domínio.
- O frontend passa a ter responsabilidade real de conversão de fuso.

## Testes obrigatórios

- Evento de dia inteiro criado em `Pacific/Kiritimati` (UTC+14) aparece no mesmo dia para um usuário em
  `America/Los_Angeles` (UTC−8).
- Evento com horário criado às 14h em `America/Sao_Paulo` é lido como o mesmo `Instant` por um usuário em
  `Europe/Lisbon`.
- Série semanal às 09:00 atravessando uma transição de horário de verão mantém 09:00 civis e muda o
  `Instant`.
- Horário inexistente e horário ambíguo produzem o resultado documentado na tabela do item 4.
- Nenhuma entidade ou DTO declara `LocalDateTime` (verificado por teste ArchUnit).
- Evento com `ends_at == starts_at` é rejeitado (`RN-EVT-03`).
- Eventos adjacentes (`fim de A == início de B`) não são conflito.

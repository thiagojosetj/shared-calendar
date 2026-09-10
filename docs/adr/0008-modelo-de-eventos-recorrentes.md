# ADR-0008 — Modelo de eventos recorrentes

- **Status:** Aceito
- **Data:** 2026-09-09
- **Regras atendidas:** `RN-REC-01` a `RN-REC-10`, `RN-TZ-06`, `RN-INT-03`

## Contexto

A spec classifica recorrência como área de alto risco, proíbe inventar formato próprio e proíbe materializar
ocorrências infinitas no banco. A implementação é da Fase 4, mas o **modelo** precisa ser decidido antes,
porque ele determina colunas em `event` e uma tabela nova — e mudar isso depois significa migrar dados.

O problema difícil não é gerar as datas. É que uma série é uma entidade **e** um gerador de entidades ao
mesmo tempo: "editar a reunião de quarta" pode significar três coisas completamente diferentes.

## Decisão

**Armazenar a regra em `RRULE` do RFC 5545, expandir sob demanda, e representar desvios em uma tabela de
exceções — nunca materializar a série.**

### Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| Colunas relacionais próprias (`freq`, `interval`, `byday`, `until`, `count`) | Legível, mas é reinventar o RFC 5545 pela metade. Na hora de exportar `.ics` (`RN-INT-01`) seria preciso traduzir de volta, e toda regra não prevista viraria uma coluna nova. |
| Materializar as ocorrências como linhas | Consulta trivial, mas proibido pela spec e com razão: séries sem fim não têm quantas linhas gerar, e editar a série vira uma migração de dados. |
| Materializar com janela deslizante e job de manutenção | Resolve o infinito, mas troca um problema de consulta por um problema de consistência: uma série editada precisa reescrever a janela, e o job vira um ponto de falha permanente. |
| **`RRULE` + exceções + expansão sob demanda** ✅ | Uma linha por série, formato padrão, interoperável, e a complexidade fica em código puro e testável em vez de em dados. |

### 1. Modelo

O próprio `event` é o **mestre da série**:

```sql
recurrence_rule text NULL       -- RRULE do RFC 5545, ex.: FREQ=WEEKLY;BYDAY=MO,WE;UNTIL=20261231T235959Z
```

`recurrence_rule NULL` significa evento simples. O fuso de referência é o `time_zone` já definido no
[ADR-0002](0002-datas-horas-e-fuso-horario.md), e é ele que ancora a expansão.

Desvios ficam em tabela própria:

```sql
CREATE TABLE event_occurrence_exception (
    series_event_id  uuid        NOT NULL REFERENCES event(id) ON DELETE CASCADE,
    occurrence_start timestamptz NOT NULL,   -- a data/hora ORIGINAL da ocorrência (RECURRENCE-ID)
    kind             varchar(16) NOT NULL,   -- CANCELLED | DELETED | MODIFIED
    override_event_id uuid       NULL REFERENCES event(id),  -- preenchido quando kind = MODIFIED
    PRIMARY KEY (series_event_id, occurrence_start)
);
```

**A identidade de uma ocorrência é o par `(série, início original)`**, exatamente a semântica de
`RECURRENCE-ID` do RFC 5545. É "original" e não "atual" de propósito: se a ocorrência foi movida das 14h
para as 16h, a chave continua sendo 14h — caso contrário, mover duas vezes perderia o vínculo.

Uma ocorrência modificada vira um `event` normal, referenciado por `override_event_id`. Assim ela ganha
participantes, RSVP, notas e comentários próprios sem nenhum modelo paralelo.

### 2. As três semânticas de edição

| Operação | O que acontece |
|---|---|
| **Somente esta ocorrência** | Cria um `event` com os novos valores e uma linha `MODIFIED` apontando para ele. A série não muda. |
| **Esta e as próximas** | **Divide a série**: a série original recebe `UNTIL` imediatamente antes desta ocorrência; uma nova série é criada começando aqui, com os novos valores. As exceções posteriores ao ponto de corte migram para a nova série. |
| **Toda a série** | Edita o mestre. As ocorrências com `MODIFIED` **mantêm** os campos que sobrescreveram (`RN-REC-06`). |

A divisão é a operação mais delicada e a que mais quebra em implementações caseiras. Duas armadilhas que
precisam de teste:

- migrar as exceções posteriores — esquecer isso faz uma ocorrência cancelada "ressuscitar" após a divisão;
- ajustar `COUNT` quando a série original usa contagem em vez de `UNTIL` — dividir uma série de 10
  ocorrências na quarta deve deixar 3 na primeira e 7 na segunda, não 10 e 10.

Excluir e cancelar têm as mesmas três semânticas (`RN-REC-05`), gravando `DELETED` ou `CANCELLED` na tabela
de exceções. Cancelar uma ocorrência **não** a apaga: ela continua aparecendo, marcada como cancelada.

### 3. Expansão sob demanda, sempre limitada

A consulta do calendário recebe um período. Para cada série que possa intersectá-lo, a expansão gera as
ocorrências **dentro daquele período**, aplica as exceções e mescla com os eventos simples.

Duas proteções obrigatórias:

- **Limite rígido de ocorrências por consulta.** Uma regra diária consultada num intervalo de 10 anos
  geraria ~3.650 ocorrências por série. O período máximo consultável já limita isso, mas o expansor também
  tem um teto próprio e falha explicitamente ao ultrapassá-lo, em vez de degradar em silêncio.
- **Horizonte máximo para séries sem fim.** Uma série sem `UNTIL` nem `COUNT` é válida; a expansão nunca é
  chamada sem um limite superior.

A expansão acontece **em Java**, não em SQL. Fazer isso no banco exigiria implementar o RFC 5545 em SQL —
ilegível e intestável. A consequência honesta é que **paginação por página de banco não funciona** para
resultados que misturam séries e eventos simples: o número de linhas no banco não corresponde ao número de
ocorrências na tela. Por isso a API de calendário pagina **por período** (mês, semana, dia), não por
`offset`/`limit` — o que também é a forma como o usuário realmente navega em um calendário.

### 4. Horário de verão

A âncora é o horário **civil** no fuso de referência. Para cada data gerada pela regra:

```java
Instant start = date.atTime(localTime).atZone(referenceZone).toInstant();
```

Uma série semanal às 09:00 permanece às 09:00 civis atravessando qualquer transição (`RN-TZ-06`). Os casos
de horário inexistente e ambíguo seguem o comportamento documentado no ADR-0002.

### 5. Participantes e RSVP

Os participantes pertencem à **série**. O RSVP pode ser sobrescrito **por ocorrência** (`RN-REC-10`) — "vou a
todas menos na próxima quarta" é o caso de uso real. Como uma ocorrência modificada já é um `event` próprio,
ela recebe RSVP próprio sem modelo adicional.

Isso é pós-MVP. No primeiro momento, RSVP vale para a série inteira.

### 6. Escolha da biblioteca de expansão: decidida no momento da implementação

Este ADR decide o **modelo**, que é a parte cara de reverter. A escolha da biblioteca que expande a `RRULE`
é deliberadamente adiada para a Fase 4, com critérios fixados agora:

1. licença compatível com um projeto de portfólio público;
2. manutenção ativa verificada na data da escolha (não "era popular em 2019");
3. cobre `FREQ`, `INTERVAL`, `BYDAY`, `BYMONTHDAY`, `UNTIL`, `COUNT` e `EXDATE`;
4. expande em uma janela sem exigir materialização;
5. não arrasta um framework de calendário inteiro se só precisamos da recorrência.

Candidatos a avaliar: `dmfs lib-recur`, `ical4j`, `biweekly`. Implementar do zero é a última opção — a regra
`BYDAY=-1SU` ("último domingo") e a interação entre `BYMONTHDAY` e meses curtos são exatamente onde
implementações caseiras erram.

Escrever a versão hoje seria escrever um número que estará desatualizado quando a Fase 4 chegar, o que este
projeto proíbe explicitamente.

### 7. O que fica fora

`RN-REC-01` define o que a **interface** oferece: diária, semanal com dias específicos, mensal, intervalo, e
término por data ou contagem. Regras mais exóticas do RFC 5545 (`BYSETPOS`, `BYWEEKNO`, `BYYEARDAY`) podem
chegar por importação de `.ics` e devem ser **exibidas** corretamente, mas não precisam ser editáveis pela
interface (`RN-REC-09`). O modelo suporta porque armazena a `RRULE` crua.

## Consequências

**Positivas.** Uma linha por série. Formato padrão, o que torna `.ics` uma tradução e não uma conversão.
Sem job de manutenção e sem risco de dados dessincronizados. A complexidade fica em código testável.

**Negativas.** Toda consulta de calendário passa por um passo de expansão em memória. Paginação por offset
deixa de ser possível para o calendário (aceito: paginamos por período). Detecção de conflito e
disponibilidade também precisam expandir séries, o que torna o expansor um componente crítico de
desempenho — e uma razão a mais para ele ser puro e ter teste de carga simples.

## Testes obrigatórios

- Semanal às segundas e quartas gera exatamente as datas esperadas em um mês.
- `COUNT=10` gera 10 ocorrências e para.
- `UNTIL` no meio de um dia inclui/exclui a ocorrência daquele dia conforme o RFC.
- Mensal no dia 31 pula meses que não têm dia 31 (não cai no dia 1º do mês seguinte).
- Série às 09:00 atravessando transição de horário de verão mantém 09:00 civis.
- "Somente esta" não afeta as demais ocorrências.
- "Esta e as próximas" divide a série, e uma ocorrência cancelada **depois** do corte continua cancelada.
- "Esta e as próximas" em série com `COUNT` distribui a contagem corretamente.
- "Toda a série" preserva os campos sobrescritos por uma ocorrência modificada.
- Cancelar uma ocorrência a mantém visível como cancelada; excluir a remove da expansão.
- Expansão de série sem fim em janela de um mês não gera mais que o teto configurado.
- Consulta de um mês com 20 séries executa um número constante de queries.

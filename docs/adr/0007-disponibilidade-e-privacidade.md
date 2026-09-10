# ADR-0007 — Disponibilidade free/busy e privacidade

- **Status:** Aceito
- **Data:** 2026-09-09
- **Regras atendidas:** `RN-AVL-01` a `RN-AVL-09`, `RN-CFL-05`, `RN-SRC-03`, `RN-SRC-04`

## Contexto

"Encontrar um horário em que todos estão livres" é a funcionalidade de destaque do produto. Ela carrega uma
regra que a spec marca como inegociável:

> Saber que uma pessoa está ocupada **não** dá direito de ver o evento que causa a indisponibilidade.

Uma implementação ingênua — carregar os eventos dos participantes e devolver os intervalos — vaza tudo:
basta olhar o JSON. E mesmo que a interface esconda, o dado já saiu do servidor.

## Decisão

**O tipo de retorno é o mecanismo de controle de vazamento, não a disciplina do desenvolvedor.**

### 1. Uma projeção que não sabe o que é um título

O repositório de disponibilidade não devolve entidades `Event`. Devolve um `record` que **só tem os campos
que a disponibilidade precisa**:

```java
public record BusyInterval(UUID userId, Instant start, Instant end) {}
```

Não existe campo de título, descrição, local, grupo ou identificador de evento. Não é possível vazar o que
não foi carregado — nem por engano em um DTO, nem por um `toString()` em log, nem por um campo esquecido em
uma serialização futura.

Consulta dedicada, com projeção no nível do JPQL/SQL. **Não** se reaproveita o caminho normal de leitura de
evento com um filtro depois: reaproveitar significaria que os dados sensíveis chegaram à memória e que a
proteção depende de alguém lembrar de removê-los.

### 2. O algoritmo é domínio puro

`AvailabilityCalculator` é uma classe sem Spring, sem JPA e sem banco. Recebe listas de intervalos e
devolve listas de intervalos:

1. **Normalizar** — cada evento vira um `BusyInterval`. Evento de dia inteiro vira o dia completo no fuso do
   dono do calendário (`RN-AVL-05`); evento com horário já é um par de instantes.
2. **Filtrar** — não contam: eventos com RSVP `NOT_GOING` daquele usuário, eventos `CANCELLED` e eventos na
   lixeira (`RN-AVL-04`).
3. **Mesclar** — ordenar por início e fundir intervalos sobrepostos ou adjacentes de cada usuário.
4. **Unir** — juntar os intervalos ocupados de todos os participantes e mesclar de novo. O resultado é
   "algum deles está ocupado".
5. **Complementar** — dentro da janela consultada, o complemento dessa união é o conjunto de intervalos em
   que **todos** estão livres.

Ser domínio puro é o que torna os casos-limite (intervalos idênticos, aninhados, adjacentes, de duração
zero, atravessando meia-noite, atravessando mudança de horário de verão) testáveis em milissegundos, sem
banco.

### 3. Duração mínima entra depois sem reescrever nada

`RN-AVL-09` pede que a funcionalidade sirva sem "duração desejada", mas que ela possa ser acrescentada. O
passo 5 devolve uma lista de intervalos livres; filtrar por duração mínima é um `filter` sobre essa lista.

Portanto a assinatura já nasce preparada, com um parâmetro opcional que hoje tem valor neutro:

```java
List<FreeInterval> commonFreeIntervals(
        List<BusyInterval> busy, Interval window, Duration minimumDuration);
```

Isso não é abstração especulativa: é um parâmetro com valor padrão `Duration.ZERO`, não uma camada.

### 4. Quem pode consultar quem

**Somente usuários com quem o solicitante compartilha ao menos um grupo** (`RN-AVL-06`). Verificado no
backend, para cada participante da consulta, antes de qualquer leitura de calendário.

Sem essa regra, o endpoint viraria um oráculo: qualquer pessoa poderia mapear a rotina de qualquer usuário
do sistema conhecendo apenas seu identificador público.

### 5. Limites que impedem o endpoint de virar ferramenta de vigilância

Compartilhar um grupo dá direito a coordenar um compromisso, não a extrair o padrão de vida de alguém.
Portanto (`RN-AVL-07`):

| Limite | Valor inicial | Por quê |
|---|---|---|
| Período máximo por consulta | 31 dias | Impede varrer um ano e reconstruir a rotina completa |
| Participantes por consulta | 20 | Impede varredura em massa do grupo |
| Granularidade mínima do intervalo | 15 minutos | Bordas arredondadas reduzem a precisão do que se infere |
| Rate limit | por usuário autenticado | Impede compensar os limites acima com muitas consultas |

Os três primeiros são validados como regra de negócio e devolvem 422 com mensagem clara — não são "chutes"
escondidos que truncam silenciosamente.

### 6. O que a resposta contém

```jsonc
{
  "window":   { "from": "2026-09-07T00:00:00Z", "to": "2026-09-11T23:59:59Z" },
  "timeZone": "America/Sao_Paulo",
  "freeIntervals": [
    { "start": "2026-09-08T17:00:00Z", "end": "2026-09-08T19:00:00Z" }
  ]
}
```

E **nada além disso**. Sem título, sem quem está ocupado, sem quantos estão ocupados, sem identificador de
evento. Nem sequer "João está ocupado das 15h às 16h" — o produto responde *quando todos podem*, não *o que
cada um está fazendo*.

O fuso de apresentação é escolhido pelo solicitante e vem explícito na resposta (`RN-AVL-08`), para que não
haja ambiguidade sobre o que os instantes significam.

### 7. Vazamento indireto

As regras acima protegem o canal principal. Os canais laterais precisam de atenção própria:

- **Contagem.** Não devolver "N participantes ocupados" — a diferença entre 1 e 2 já é informação.
- **Mensagem de erro.** Consultar alguém com quem não se compartilha grupo devolve a mesma resposta de
  consultar alguém inexistente. Caso contrário, o endpoint vira um verificador de existência de usuários.
- **Detecção de conflito.** `RN-CFL-05` é a mesma regra em outro lugar: o aviso de conflito só cita o título
  de eventos que o solicitante já podia ver; para os demais, no máximo "há um compromisso neste horário".
- **Autocomplete e pesquisa.** Mesmo conjunto de permissões da API normal (`RN-SRC-02`).

## Consequências

**Positivas.** O vazamento é impedido pelo tipo, não por revisão de código. O núcleo do cálculo é testável
sem infraestrutura. A regra de privacidade fica demonstrável em entrevista com um teste que a nomeia.

**Negativas.** Uma consulta e uma projeção a mais para manter, que duplicam parcialmente a leitura de
eventos. É duplicação deliberada: unificá-las reintroduziria exatamente o risco que o ADR existe para
eliminar. Os limites do item 5 são conservadores e podem incomodar em uso legítimo; são configuráveis.

## Testes obrigatórios

- A resposta JSON de disponibilidade **não contém** nenhum campo de conteúdo de evento (verificado sobre o
  JSON serializado, não sobre o DTO).
- `BusyInterval` não possui campo de conteúdo (verificado por ArchUnit/reflexão).
- Consultar disponibilidade de usuário com quem não se compartilha grupo é indistinguível de consultar
  usuário inexistente.
- Evento com RSVP `NOT_GOING`, cancelado ou na lixeira não gera `BUSY`.
- Evento de dia inteiro ocupa o dia inteiro no fuso do dono.
- Intervalos adjacentes (`fim de A == início de B`) são mesclados; a lacuna entre eles é zero, não negativa.
- Janela sem nenhum evento devolve a janela inteira livre.
- Todos ocupados o tempo todo devolve lista vazia, não erro.
- Período acima do máximo e participantes acima do máximo devolvem 422.
- Cálculo com `minimumDuration` de 30 minutos descarta janelas de 20 minutos.

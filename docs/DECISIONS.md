# DECISIONS — Índice de decisões técnicas

Este arquivo é o **índice** das decisões do projeto. Decisões duradouras e caras de reverter ganham um ADR
próprio em [`adr/`](adr/). Decisões menores, mas que vale a pena registrar para não serem repensadas do
zero, ficam listadas aqui mesmo.

## Como um ADR funciona neste projeto

- Arquivo: `docs/adr/NNNN-titulo-em-kebab-case.md`.
- Seções: contexto, alternativas consideradas, decisão, consequências, status.
- Status possíveis: `Proposto`, `Aceito`, `Substituído por ADR-NNNN`.
- Um ADR aceito **não é reescrito** quando a decisão muda. Cria-se um novo ADR e marca-se o antigo como
  substituído. O histórico da decisão faz parte do valor do documento.
- Todo ADR referencia as regras `RN-*` de [`../PROJECT_SPEC.md`](../PROJECT_SPEC.md) que ele atende.

## Índice de ADRs

| ADR | Título | Decisão em uma linha | Status |
|---|---|---|---|
| [0001](adr/0001-estrutura-do-repositorio-e-arquitetura-do-backend.md) | Estrutura do repositório e arquitetura do backend | Monorepo; pacotes por domínio com regra de dependência verificada por ArchUnit; UUID v7; testes contra PostgreSQL real | Aceito |
| [0002](adr/0002-datas-horas-e-fuso-horario.md) | Datas, horas e fuso horário | `Instant` para momentos, `LocalDate` para dia inteiro, fuso de referência guardado junto; `LocalDateTime` proibido; `Clock` injetável | Aceito |
| [0003](adr/0003-autenticacao-e-sessao.md) | Autenticação e sessão | Sessão server-side com cookie `HttpOnly` em vez de JWT, pela revogação imediata; Google sem vínculo automático por e-mail | Aceito |
| [0004](adr/0004-modelo-de-autorizacao-rbac.md) | Modelo de autorização (RBAC) | Papéis fixos em enum + overrides, resolvidos por um `PermissionResolver` único e aplicados por policies de domínio | Aceito |
| [0005](adr/0005-eventos-em-multiplos-grupos.md) | Eventos em múltiplos grupos | Junção `event_group` com grupos-pares; autoridade ancorada na autoria imutável; agregação `ANY`/`ALL` por operação | Aceito |
| [0006](adr/0006-soft-delete-lixeira-e-auditoria.md) | Soft delete, lixeira e auditoria | `@SQLRestriction` + repositório dedicado para a lixeira; purga idempotente em lotes; audit log polimórfico que sobrevive à purga | Aceito |
| [0007](adr/0007-disponibilidade-e-privacidade.md) | Disponibilidade e privacidade | A projeção `BusyInterval` não tem campo de conteúdo — o tipo impede o vazamento; cálculo é domínio puro | Aceito |
| [0008](adr/0008-modelo-de-eventos-recorrentes.md) | Eventos recorrentes | `RRULE` do RFC 5545 + tabela de exceções + expansão sob demanda; nunca materializar | Aceito |
| [0009](adr/0009-plataforma-do-frontend.md) | Plataforma do frontend | FullCalendar (plugins MIT) atrás de adaptador; CSS Modules + tokens; TanStack Query; tipos gerados do OpenAPI | Aceito |

## Decisões menores registradas

### D-01 — Localização e identidade do repositório

**Decisão.** O repositório pertence à conta pessoal `thiagojosetj`, e a identidade Git é configurada
**apenas no repositório local** (`--local`), nunca globalmente.

**Contexto.** Configurar a identidade por repositório evita que commits deste projeto saiam com outra
identidade configurada na máquina.

### D-02 — Autenticação Git por HTTPS, não por SSH

**Decisão.** Usar HTTPS + Git Credential Manager. Não gerar chave SSH dedicada nem criar alias por
enquanto.

**Contexto.** Uma chave SSH separada e um alias só se justificam quando há mais de uma conta GitHub no
mesmo ambiente. Sem essa necessidade, a estrutura adicionaria uma peça a mais para manter.

**Quando revisitar.** Se for preciso usar mais de uma conta GitHub no mesmo ambiente, ou por preferência
por SSH.

### D-03 — Monorepo com backend e frontend no mesmo repositório

**Decisão.** Um único repositório contendo `backend/`, `frontend/` e `docs/`.

**Contexto.** Backend e frontend evoluem juntos, compartilham o contrato da API e são desenvolvidos pela
mesma pessoa. Repositórios separados exigiriam sincronizar versões de contrato entre eles, o que é custo
puro nesta escala. Como peça de portfólio, um monorepo também apresenta o projeto inteiro em um único link.

**Consequência.** A CI precisa saber executar dois toolchains diferentes e, idealmente, só rodar o que foi
afetado.

### D-04 — Status de evento reduzido no MVP

**Decisão.** O MVP persiste apenas `CONFIRMED` e `CANCELLED` (`RN-EVT-10`).

**Contexto.** A especificação listava cinco status: planejado, confirmado, em andamento, concluído e
cancelado. "Em andamento" e "concluído" são funções do horário atual — persistir esses valores exigiria uma
rotina para mantê-los sincronizados com o relógio, e qualquer falha dessa rotina deixaria o dado errado na
tela. "Planejado" e "confirmado" não têm, no MVP, nenhuma regra que os diferencie.

**Consequência.** "Em andamento" e "concluído" são **calculados** na leitura (`RN-EVT-11`). Se no futuro
surgir um fluxo de trabalho em que o usuário marca manualmente um evento como concluído, isso vira um campo
distinto do status do evento, não uma extensão dele (`RN-EVT-12`).

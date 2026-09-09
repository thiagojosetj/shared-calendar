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

| ADR | Título | Status |
|---|---|---|
| — | *Em elaboração (item F0-02 do roadmap).* | — |

## Decisões menores registradas

### D-01 — Localização e identidade do repositório

**Decisão.** O projeto vive em `E:\Estudos\Programacao\projetos-com-codex\shared-calendar`, junto dos
demais projetos pessoais da máquina. A identidade Git é configurada **apenas no repositório local**
(`--local`), nunca globalmente.

**Contexto.** A especificação original assumia um computador de trabalho com conta profissional de GitHub
a preservar. O diagnóstico de 2026-09-09 mostrou que a máquina de desenvolvimento é pessoal, que a
identidade Git global **já é a pessoal**, e que não há conta profissional para isolar.

**Consequência.** As regras de isolamento de conta profissional continuam registradas em `AGENTS.md` e
voltam a valer integralmente se o projeto for aberto em um computador de trabalho.

### D-02 — Autenticação Git por HTTPS, não por SSH

**Decisão.** Usar HTTPS + Git Credential Manager, como os demais repositórios pessoais desta máquina. Não
gerar chave SSH dedicada nem criar alias `github-personal` por enquanto.

**Contexto.** A especificação previa uma chave SSH separada e um alias para isolar contas. Sem uma segunda
conta na máquina, essa estrutura resolveria um problema inexistente e adicionaria uma peça a mais para
manter. Os repositórios `studyflow` e `pagina-investimentos` já usam HTTPS com sucesso.

**Quando revisitar.** Se o projeto passar a ser desenvolvido também em um computador com conta
profissional configurada, ou se o usuário quiser usar SSH por preferência.

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

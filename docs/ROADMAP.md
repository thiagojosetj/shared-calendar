# ROADMAP — Shared Calendar

Ordem de construção do projeto. Cada item tem identificador estável, critérios de aceite verificáveis e
uma Definition of Done.

Este documento diz **em que ordem** construir. O **que** construir está em
[`../PROJECT_SPEC.md`](../PROJECT_SPEC.md), com as regras `RN-*` referenciadas aqui.

## Princípios

1. **Fatias verticais.** Preferir uma funcionalidade que atravessa banco → backend → API → frontend a uma
   camada inteira feita de uma vez.
2. **Sempre executável.** Ao final de cada item, o projeto deve continuar subindo e com a suíte verde.
3. **Sem scaffolding massivo.** Não criar entidades, tabelas ou telas de fases futuras "já que estamos aqui".
4. **Regra de negócio nasce com teste.** Toda regra `RN-*` implementada ganha ao menos um teste que a cita.

## Definition of Done (padrão)

Um item está concluído quando, proporcionalmente ao seu escopo:

- a funcionalidade está implementada e o projeto sobe;
- as regras `RN-*` listadas no item estão respeitadas;
- a autorização foi revisada (quando o item toca dados de outro usuário);
- a migration Flyway foi criada, quando o schema mudou;
- existem testes cobrindo as regras citadas, e eles passam de fato;
- lint, typecheck e build passam nas partes afetadas;
- `docs/PROJECT_STATUS.md` foi atualizado;
- o diff foi revisado e não há secrets nem arquivos acidentais;
- existe um commit coerente descrevendo a mudança.

## Legenda de prioridade

| Prioridade | Significado |
|---|---|
| P0 | Bloqueia o restante do projeto. |
| P1 | Essencial para o MVP. |
| P2 | Importante, mas o MVP funciona sem. |
| P3 | Diferencial ou evolução. |

---

# MVP

## Fase 0 — Fundação e ambiente

**Objetivo da fase:** ter um repositório documentado, uma arquitetura decidida e um esqueleto que sobe,
com banco em container e testes rodando de verdade.

### F0-01 — Documentação inicial · P0

**Objetivo.** Transformar a especificação bruta em documentos organizados e rastreáveis.

**Dependências.** Nenhuma.

**Critérios de aceite.**
- `PROJECT_SPEC.md` contém as regras de negócio numeradas e os casos de uso de referência.
- `README.md` descreve o problema, a proposta e o status real.
- `docs/ROADMAP.md`, `docs/DECISIONS.md` e `docs/PROJECT_STATUS.md` existem.
- `.gitignore` cobre secrets, builds, IDE e artefatos; `.gitattributes` normaliza fim de linha.

**Verificação.** Leitura dos documentos; `git status` limpo após o commit.

### F0-02 — Decisões arquiteturais registradas · P0

**Objetivo.** Decidir e documentar, antes de escrever código, as escolhas caras de reverter.

**Dependências.** F0-01.

**Critérios de aceite.**
- Existe um ADR aceito para cada tema: estratégia de autenticação; modelo de RBAC; estratégia de datas e
  fusos; modelo de recorrência; eventos em múltiplos grupos; disponibilidade e privacidade; soft delete e
  auditoria; estrutura do repositório e do backend; plataforma do frontend.
- Cada ADR lista alternativas reais consideradas, a decisão e as consequências.
- `docs/DECISIONS.md` indexa todos eles.

**Verificação.** Cada ADR referencia as regras `RN-*` que ele atende.

### F0-03 — Infraestrutura local com Docker Compose · P0

**Objetivo.** PostgreSQL reproduzível, com nomes e volume próprios do projeto.

**Dependências.** F0-02.

**Critérios de aceite.**
- `docker compose up -d` sobe o PostgreSQL com healthcheck.
- Rede, container e volume têm nomes exclusivos do projeto (não colidem com outros projetos da máquina).
- `.env.example` documenta todas as variáveis, sem valores reais; `.env` está no `.gitignore`.
- `docs/local-development.md` explica pré-requisitos, portas, comandos e troubleshooting.

**Verificação.** Subir, conectar, derrubar e subir de novo preservando o volume.

### F0-04 — Esqueleto do backend Spring Boot · P0

**Objetivo.** Aplicação que sobe, conecta no banco, aplica migrations e responde a um health check.

**Dependências.** F0-03.

**Critérios de aceite.**
- Projeto Maven com wrapper versionado; `./mvnw` funciona sem Maven instalado.
- Estrutura de pacotes por domínio conforme o ADR de arquitetura do backend.
- Flyway aplica a migration inicial; `ddl-auto` está em `validate` ou `none`.
- Endpoint de health responde.
- Perfis `dev` e `test` separados; nenhuma credencial no código.
- Tratamento global de erros retornando um formato padronizado, sem stack trace.

**Verificação.** `./mvnw verify` passa; a aplicação sobe contra o Postgres do Compose.

### F0-05 — Esqueleto do frontend React + Vite + TypeScript · P0

**Objetivo.** SPA que sobe, tipa, linta e consegue chamar o backend em desenvolvimento.

**Dependências.** F0-04.

**Critérios de aceite.**
- Projeto Vite com TypeScript em modo estrito.
- Lint (oxlint, ver ADR-0009) e Prettier configurados e passando.
- Proxy de desenvolvimento para o backend funcionando.
- Uma página que consome o health check do backend e trata carregamento e erro.
- Layout base responsivo com o menu conceitual (`RN-UX-01`), ainda sem funcionalidades.

**Verificação.** `npm run lint`, `npm run typecheck` e `npm run build` passam; a página exibe o resultado real.

### F0-06 — Testes mínimos e CI · P1

**Objetivo.** Provar que a fundação é testável antes de construir sobre ela.

**Dependências.** F0-04, F0-05.

**Critérios de aceite.**
- Teste de integração que sobe o contexto Spring contra um PostgreSQL real via Testcontainers e valida que
  as migrations aplicam.
- Teste de componente do frontend rodando.
- Workflow do GitHub Actions executando build, lint, typecheck e testes.

**Verificação.** Uma execução real da CI concluída com sucesso, observada e citada.

---

## Fase 1 — Autenticação, perfil e calendário pessoal

**Objetivo da fase:** um usuário consegue se cadastrar, entrar, ver seu perfil e ter um calendário pessoal
privado. É a primeira fatia vertical completa.

### F1-01 — Cadastro e login com e-mail e senha · P0

**Regras.** `RN-AUTH-01`, `RN-AUTH-02`, `RN-AUTH-03`, `RN-AUTH-06`, `RN-AUTH-08`, `RN-USR-01`, `RN-USR-02`, `RN-USR-03`.

**Critérios de aceite.**
- Cadastro valida entrada no backend e normaliza o e-mail.
- Senha armazenada apenas como hash.
- Login autentica e estabelece a credencial de sessão conforme o ADR de autenticação.
- Logout invalida a credencial no servidor.
- Mensagens não revelam se um e-mail existe.
- Cada usuário recebe identificador público único na criação.

**Testes.** Cadastro com e-mail duplicado; login com senha errada; resposta indistinguível para e-mail
inexistente; logout invalidando o acesso; hash nunca retornado pela API.

### F1-02 — Perfil do usuário e timezone · P1

**Regras.** `RN-USR-03`, `RN-USR-04`, `RN-USR-05`, `RN-USR-06`, `RN-USR-08`, `RN-TZ-01`.

**Critérios de aceite.**
- O usuário lê e edita o próprio perfil, incluindo timezone.
- A busca por identificador público retorna apenas dados públicos.
- Não existe endpoint que liste todos os usuários.

**Testes.** Um usuário não consegue ler nem editar o perfil de outro; a busca pública não vaza e-mail.

### F1-03 — Calendário pessoal automático · P1

**Regras.** `RN-CAL-01`, `RN-CAL-02`, `RN-CAL-03`.

**Critérios de aceite.**
- Criar a conta cria o calendário pessoal na mesma transação.
- O calendário pessoal só é acessível ao dono.

**Testes.** Usuário A não acessa o calendário pessoal de B.

### F1-04 — Telas de autenticação e sessão no frontend · P1

**Regras.** `RN-UX-06`, `RN-UX-07`, `RN-UX-09`.

**Critérios de aceite.**
- Telas de cadastro, login e perfil, responsivas e navegáveis por teclado.
- Rotas protegidas; estados de carregamento e erro tratados.
- Nenhum segredo persistido no navegador além do previsto pelo ADR de autenticação.

### F1-05 — Login com Google (OAuth2) · P1

**Regras.** `RN-AUTH-04`, `RN-AUTH-05`, `RN-AUTH-10`.

**Critérios de aceite.**
- Fluxo Authorization Code conduzido pelo backend.
- E-mail verificado do Google que coincide com conta existente vincula identidades em vez de duplicar.
- Credenciais apenas em variáveis de ambiente; `.env.example` atualizado.

**Testes.** Vinculação de identidade; recusa de e-mail não verificado.

### F1-06 — Rate limiting no login · P2

**Regras.** `RN-AUTH-07`.

**Critérios de aceite.** Tentativas excessivas são bloqueadas temporariamente sem revelar se o e-mail existe.

---

## Fase 2 — Grupos, papéis e permissões

**Objetivo da fase:** o motor de autorização do produto, com testes exaustivos. É o item mais importante do
portfólio.

### F2-01 — CRUD de grupos e associação de membros · P0

**Regras.** `RN-GRP-01` a `RN-GRP-07`, `RN-CAL-04`.

### F2-02 — Papéis e motor central de permissões · P0

**Regras.** `RN-AUTZ-01` a `RN-AUTZ-06`, `RN-AUTZ-20`, `RN-AUTZ-21`, `RN-AUTZ-24`, `RN-AUTZ-30` a `RN-AUTZ-32`.

**Critérios de aceite.**
- Toda decisão de autorização passa por um ponto central; não há condicional de permissão espalhada.
- A matriz papel × permissão é explícita e testada.

**Testes.** Matriz completa de papel × operação; escalada de privilégio recusada; resposta indistinguível
entre "não existe" e "sem permissão" onde aplicável.

### F2-03 — Entrada em grupo por link, código e identificador público · P1

**Regras.** `RN-GRP-10` a `RN-GRP-16`.

**Testes.** Token expirado, revogado e esgotado retornam resposta genérica; entrada é idempotente.

### F2-04 — Saída, remoção e transferência de propriedade · P1

**Regras.** `RN-GRP-20` a `RN-GRP-22`, `RN-AUTZ-40` a `RN-AUTZ-42`.

**Testes.** A transferência é atômica; nunca existem dois donos nem zero donos; `ADMIN` não consegue
transferir nem excluir o grupo.

### F2-05 — Telas de grupos e membros · P1

**Regras.** `RN-UX-07`, `RN-UX-08`.

---

## Fase 3 — Eventos, participantes e calendário

### F3-01 — CRUD de eventos com autoria · P0

**Regras.** `RN-EVT-01` a `RN-EVT-04`, `RN-EVT-20` a `RN-EVT-22`, `RN-AUTZ-10`, `RN-AUTZ-11`, `RN-TZ-02` a `RN-TZ-05`, `RN-TZ-07`, `RN-TZ-08`.

**Testes.** Criador edita e exclui o próprio evento; `VIEWER` não cria; evento de dia inteiro não vira
instante; participantes em fusos diferentes veem o mesmo instante.

### F3-02 — Eventos em múltiplos grupos · P0

**Regras.** `RN-MGR-01` a `RN-MGR-09`.

**Testes.** Evento único visível em dois grupos; remover de um grupo não afeta o outro; zero grupos vira
evento pessoal; participante em ambos os grupos não duplica.

### F3-03 — Participantes e RSVP · P0

**Regras.** `RN-RSVP-01` a `RN-RSVP-09`.

**Testes.** `VIEWER` responde RSVP; ninguém responde pelo outro; convidar todos do grupo resolve no momento
do convite.

### F3-04 — Cancelamento, soft delete e lixeira · P1

**Regras.** `RN-DEL-01` a `RN-DEL-04`, `RN-DEL-07` a `RN-DEL-10`.

**Testes.** Cancelado continua visível; excluído some das visualizações; restauração respeita permissão.

### F3-05 — Visualizações de calendário e visão agregada · P1

**Regras.** `RN-CAL-05` a `RN-CAL-08`, `RN-UX-04`, `RN-UX-06`.

**Critérios de aceite.**
- Mês, semana, dia e agenda/lista.
- Seleção de quais calendários aparecem, com cores acessíveis.
- Consulta por período no backend, sem N+1 e sem materializar cópias por usuário.

---

## Fase 4 — Calendário avançado

| Item | Escopo | Regras | Prioridade |
|---|---|---|---|
| F4-01 | Eventos recorrentes: série, ocorrência, exceção e as três semânticas de edição | `RN-REC-01` a `RN-REC-08`, `RN-TZ-06` | P1 |
| F4-02 | Detecção de conflitos com aviso, respeitando privacidade | `RN-CFL-01` a `RN-CFL-06` | P2 |
| F4-03 | Drag-and-drop e resize com validação no backend | `RN-UX-05` | P2 |
| F4-04 | Pesquisa global respeitando permissões | `RN-SRC-01` a `RN-SRC-04` | P2 |
| F4-05 | Filtros do calendário aplicados no backend | `RN-SRC-05`, `RN-SRC-06` | P2 |

---

# Pós-MVP

## Fase 5 — Colaboração e auditoria

| Item | Escopo | Regras | Prioridade |
|---|---|---|---|
| F5-01 | Notas de evento e de grupo, privadas e compartilhadas | `RN-NOT-01` a `RN-NOT-04` | P2 |
| F5-02 | Comentários em eventos | `RN-CMT-01` a `RN-CMT-04` | P2 |
| F5-03 | Checklists opcionais | `RN-CHK-01`, `RN-CHK-02`, `RN-CHK-04` | P3 |
| F5-04 | Responsáveis opcionais por atividade | `RN-CHK-03`, `RN-CHK-05` | P3 |
| F5-05 | Audit log de ações de negócio | `RN-AUD-01` a `RN-AUD-07` | P2 |
| F5-06 | Purga agendada e idempotente da lixeira | `RN-DEL-05`, `RN-DEL-06` | P2 |

## Fase 6 — Notificações e lembretes

| Item | Escopo | Regras | Prioridade |
|---|---|---|---|
| F6-01 | Central de notificações interna | `RN-NTF-01` a `RN-NTF-06` | P2 |
| F6-02 | Lembretes pessoais e obrigatórios | `RN-LMB-01` a `RN-LMB-05`, `RN-LMB-08` | P2 |
| F6-03 | Processamento agendado idempotente de lembretes | `RN-LMB-06`, `RN-LMB-07` | P2 |
| F6-04 | Web Push no navegador | `RN-NTF-07` | P3 |
| F6-05 | Atualização em tempo real via WebSocket | `RN-NTF-08` | P3 |

## Fase 7 — Disponibilidade

| Item | Escopo | Regras | Prioridade |
|---|---|---|---|
| F7-01 | Projeção free/busy com privacidade imposta no backend | `RN-AVL-02` a `RN-AVL-05` | P2 |
| F7-02 | Busca de horários livres em comum entre participantes | `RN-AVL-01`, `RN-AVL-08`, `RN-AVL-09` | P2 |
| F7-03 | Limites, autorização e proteção antiabuso da consulta | `RN-AVL-06`, `RN-AVL-07` | P2 |

---

# Ideias futuras

Não implementar sem antes reavaliar prioridade e custo. A arquitetura não deve inviabilizá-las, mas também
não deve ser complicada por elas.

| Item | Escopo | Regras |
|---|---|---|
| F8-01 | Exportação de evento e de calendário em `.ics` | `RN-INT-01`, `RN-INT-03` |
| F8-02 | Importação de `.ics` | `RN-INT-02` |
| F8-03 | Integrações com Google Calendar, Outlook, Apple Calendar, Meet e Teams | `RN-INT-04` |
| F9-01 | PWA instalável | `RN-UX-11` |
| F9-02 | Aplicativo mobile | `RN-NTF-09` |
| F9-03 | Funcionalidades de IA sobre a agenda | `RN-IA-01` |
| F9-04 | Conta de demonstração para recrutadores | `RN-AUTH-12` |
| F9-05 | Recuperação de senha por e-mail | `RN-AUTH-11` |
| F9-06 | Preferências completas do usuário (idioma, tema, formato de hora) | `RN-USR-07` |
| F9-07 | Janela útil configurável na busca de disponibilidade | `RN-AVL-10` |
| F9-08 | Anexos em eventos | `RN-EVT-05`, `RN-SEC-10` |
| F9-09 | Solicitação de entrada em grupo público com aprovação | `RN-GRP-17` |

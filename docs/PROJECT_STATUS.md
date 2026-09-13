# PROJECT_STATUS — Shared Calendar

Estado **real** do repositório. Este documento descreve apenas o que já existe e foi verificado. Planos
ficam em [`ROADMAP.md`](ROADMAP.md).

**Última atualização:** 2026-09-12
**Fase atual:** Fase 0 — Fundação, **concluída** com uma ressalva (a CI ainda não rodou no GitHub)
**Próxima fase:** Fase 1 — Autenticação, perfil e calendário pessoal

---

## Resumo por item

| Item | Situação | Ressalva |
|---|---|---|
| F0-01 Documentação inicial | Concluído | — |
| F0-02 Decisões arquiteturais | Concluído | Só o ADR-0003 passou por revisão adversarial |
| F0-03 Infraestrutura local | Concluído | — |
| F0-04 Esqueleto do backend | Concluído | — |
| F0-05 Esqueleto do frontend | Concluído | — |
| F0-06 Testes mínimos e CI | Concluído localmente | O workflow foi validado executando seus passos localmente; a primeira execução real no GitHub Actions depende do repositório remoto |

## Como a Fase 0 foi verificada

Verificação final de ponta a ponta em 2026-09-12, seguindo os comandos do `README.md`:

| Verificação | Resultado |
|---|---|
| `docker compose ps` | `shared-calendar-postgres Up (healthy)` |
| `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` | perfil `dev` ativo, aplicação no ar em ~4 s, nenhuma senha no log |
| `curl localhost:5173/actuator/health` (pelo proxy do Vite) | `200`, `"status":"UP"` |
| `curl localhost:5173/api/v1/qualquer` | `401`, sem `Set-Cookie: JSESSIONID` |
| `curl -X POST localhost:5173/api/v1/qualquer` | `403` (CSRF) |
| `http://localhost:5173` no navegador | página inicial com "A API está disponível."; nenhuma requisição com erro durante a verificação |

Simulação local da CI, a partir de estado limpo:

| Job | Comandos | Resultado |
|---|---|---|
| backend | `./mvnw -B clean verify` | 16 testes unitários + 7 de integração, `BUILD SUCCESS` em 24,6 s |
| frontend | `npm ci`, `format:check`, `lint`, `typecheck`, `test`, `build` | todos com `exit=0`; 17 testes; 0 vulnerabilidades |

## O que existe

### Infraestrutura

- `docker-compose.yml` com PostgreSQL 18, healthcheck, rede, volume e container com prefixo do projeto,
  porta exposta só em `127.0.0.1` e fuso UTC.
- Volume montado em `/var/lib/postgresql`, a raiz exigida pela imagem 18 (`show data_directory` →
  `/var/lib/postgresql/18/docker`). Dados persistem após `docker compose down` + `up` (verificado).
- `.env.example` só com placeholders; `.env` ignorado pelo Git.

### Backend (Spring Boot 4.1.1, Java 21)

- **Banco:** Flyway aplica `V1__create_app_user.sql`; `ddl-auto=validate`; JDBC e Jackson em UTC/ISO-8601
  (Jackson 3). A porta vem de `POSTGRES_PORT`, a mesma do Compose (verificado apontando para uma porta
  vazia).
- **Perfis:** `dev` lê o `.env` da raiz via `spring.config.import`, sem exportar variáveis (verificado no
  PowerShell sem nenhuma variável definida). `test` fica em `src/test/resources` e isola os testes mesmo com
  `SPRING_PROFILES_ACTIVE=dev` no ambiente.
- **Segurança:**
  - só `/actuator/health` é público;
  - não autenticado recebe `401`, e escrita sem token CSRF recebe `403`;
  - o *error dispatch* não mascara o status real;
  - request cache desligado e token CSRF em cookie (`csrf.spa()`), então visitantes anônimos não criam
    sessão;
  - a senha gerada pelo Spring Boot não aparece mais no log.
- **Erros:** `GlobalExceptionHandler` devolve `application/problem+json` para erros do Spring MVC e para
  exceções inesperadas (500 com mensagem genérica), relançando as exceções do Spring Security.
- **Identificadores:** `UuidV7` (RFC 9562), com `Clock` injetável.

### Frontend (React 19.2, TypeScript 6.0, Vite 8.3)

- Estrutura por feature (`app/`, `features/`, `shared/`), TypeScript estrito.
- React Router 8 com as rotas Início, Calendário, Grupos, Notas e página para endereço inexistente.
- Layout responsivo: menu lateral no desktop e barra inferior no celular (verificado no navegador em 375 px).
- Área global (pesquisa, `+ Criar`, notificações, perfil) presente e desabilitada até existir.
- Cliente axios com `withCredentials` e `withXSRFToken`.
- TanStack Query consultando o health check, com estados de carregamento, sucesso e erro com nova
  tentativa (verificado no navegador com o backend no ar e fora do ar).
- Design tokens em CSS Modules, tema claro e escuro; 32 pares de cor verificados contra WCAG AA.
- Acessibilidade: `lang="pt-BR"`, *skip link* (verificado com Tab), landmarks nomeados, `aria-current` no
  menu, nomes acessíveis nos botões de ícone.
- oxlint com regras de React, TypeScript e `jsx-a11y`, rodando com `--deny-warnings`; Prettier.

### Testes

| Suíte | Arquivo | Testes | Precisa de Docker |
|---|---|---|---|
| Backend unitário | `UuidV7Test` | 5 | Não |
| Backend camada web | `GlobalExceptionHandlerTest` | 4 | Não |
| Backend arquitetura | `ArchitectureTest` | 7 regras | Não |
| Backend integração | `FoundationIT` | 7 | Sim |
| Frontend | `http`, `getApiHealth`, `ApiStatus`, `AppLayout` | 17 | Não |

**Verificação por mutação.** Cada comportamento crítico foi confirmado introduzindo o defeito de
propósito, vendo o teste falhar e restaurando o código:

| Defeito introduzido | Teste que falhou |
|---|---|
| *Error dispatch* exigindo autenticação | `FoundationIT` (`expected: 403 but was: 401`) |
| Request cache e CSRF em sessão | `FoundationIT` (4 testes, `JSESSIONID` emitido) |
| Tratador genérico sem relançar as exceções do Spring Security | `GlobalExceptionHandlerTest` (`expected 403 but was 500`) |
| Sem tratador genérico | `GlobalExceptionHandlerTest` |
| Uma violação por regra de arquitetura | `ArchitectureTest` (7 de 7 regras) |
| Testes sem `@ActiveProfiles("test")` e `dev` no ambiente | `FoundationIT` (detalhes do health expostos) |
| `withXSRFToken: false`, 503 rejeitado, botão renomeado | testes do frontend (3 de 3) |

Uma mutação **não** foi detectada, e o motivo foi investigado: trocar `end: true` por `false` no link
"Início" não muda nada, porque o React Router já não marca o link raiz como ativo em sub-rotas. O `end`
era redundante e foi removido.

### Integração contínua

`.github/workflows/ci.yml` com os jobs de backend e frontend. YAML validado com SnakeYAML, versões atuais
das actions (`checkout@v7`, `setup-java@v6`, `setup-node@v7`) e permissão só de leitura. O `mvnw` foi
marcado como executável no Git; ele estava com modo `100644`, o que faria a CI falhar no Linux.

## O que ainda NÃO existe

- qualquer entidade JPA, repositório, endpoint de negócio ou tela com funcionalidade;
- autenticação (Fase 1);
- geração de tipos TypeScript a partir do OpenAPI (ADR-0009): depende de a API ter documentação OpenAPI,
  que entra junto com os primeiros endpoints;
- seed de desenvolvimento;
- execução real da CI no GitHub Actions;
- repositório remoto no GitHub.

## Dívidas e pontos de atenção registrados

- **Revisão adversarial dos ADRs.** Só o ADR-0003 passou por ela, e a revisão encontrou bugs reais. O
  ADR-0002 mostrou um erro de configuração ao ser implementado. Revisar pelo menos o ADR-0008 (recorrência)
  antes da Fase 4 e o ADR-0005 antes da Fase 3.
- **Exclusão de `UserDetailsServiceAutoConfiguration`.** Pode ser removida quando a Fase 1 criar um
  `UserDetailsService` próprio.
- **Emissão do cookie CSRF após o login.** O `csrf.spa()` já está ligado, mas as armadilhas de emissão e
  renovação do token após autenticar, descritas no ADR-0003, só podem ser testadas quando existir login.
- **Testes de fatia JPA.** Quando surgirem, reavaliar o singleton `static` do container (ADR-0001).

## Ambiente verificado

Diagnóstico inicial de 2026-09-09; ferramentas de frontend verificadas em 2026-09-12. Detalhes em
[`../AGENTS.md`](../AGENTS.md#ambiente-verificado).

Java 21.0.10 · Maven Wrapper 3.9.16 · Node 24.15.0 · npm 11.12.1 · Docker 29.7.2 · Docker Compose v5.5.1 ·
PostgreSQL 18.6 (container) · Git 2.53.0. PostgreSQL e GitHub CLI não estão instalados na máquina e, por
decisão do projeto, não serão.

## Configuração Git deste repositório

- Branch principal: `main`.
- Identidade configurada **apenas neste repositório** (`.git/config`), não globalmente.
- Autenticação prevista: HTTPS + Git Credential Manager, conta pessoal `thiagojosetj`.
- Mensagens de commit em português a partir de 2026-09-12.
- Remote: **ainda não configurado**.

## Correções feitas a partir de verificação

Registradas porque são exatamente o tipo de erro que passaria despercebido:

- `spring-boot-starter-session-jdbc` **não existe**; o artefato real é
  `org.springframework.session:spring-session-jdbc`.
- No Spring Boot 4 os starters mudaram de nome (`-webmvc`, `-security-oauth2-client`, starters de teste por
  módulo), e o Jackson passou para a versão 3 (`tools.jackson`, `DateTimeFeature`).
- A imagem do **PostgreSQL 18** exige o volume em `/var/lib/postgresql`.
- **Um `curl` pode mentir sobre o status:** um 403 de CSRF chegava como 401 por causa do *error dispatch*.
- **Anônimos criavam sessão HTTP** em 401 e 403, pelo request cache e pelo repositório de CSRF em sessão.
- **O `mvnw` estava sem permissão de execução** no Git e quebraria qualquer build em Linux.
- **O `create-vite` atual usa oxlint**, e não ESLint; o **TypeScript 6** já é estrito por padrão.
- No **Testcontainers 2**, `PostgreSQLContainer` fica em `org.testcontainers.postgresql`; no **Jackson 3**,
  `JsonNode.asText()` deu lugar a `asString()`; no **React Router 8**, `react-router-dom` foi unificado em
  `react-router`.

## Próximo passo

1. Criar o repositório `shared-calendar` no GitHub pessoal, configurar o remote HTTPS e fazer o primeiro
   push, observando a primeira execução real da CI.
2. Fase 1, item F1-01: cadastro e login com e-mail e senha, sessão JDBC e CSRF ponta a ponta.

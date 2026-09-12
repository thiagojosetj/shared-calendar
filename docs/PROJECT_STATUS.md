# PROJECT_STATUS — Shared Calendar

Estado **real** do repositório. Este documento descreve apenas o que já existe e foi verificado. Planos
ficam em [`ROADMAP.md`](ROADMAP.md).

**Última atualização:** 2026-09-12
**Fase atual:** Fase 0 — Fundação
**Concluído:** F0-01, F0-02, F0-03
**Validado com pendências:** F0-04
**Parcial:** F0-06 — teste de integração com Testcontainers pronto; teste de frontend e CI pendentes

---

## O que já existe

| Área | Situação | Verificado como |
|---|---|---|
| Documentação de produto | `PROJECT_SPEC.md` com regras `RN-*`, glossário e casos de uso | leitura |
| Regras de trabalho | `AGENTS.md` com ambiente verificado e convenções | leitura |
| Roadmap | `docs/ROADMAP.md` com fases, itens e critérios de aceite | leitura |
| ADRs | Nove decisões aceitas, indexadas em `docs/DECISIONS.md` | leitura |
| Banco em container | PostgreSQL 18.6 via Docker Compose | `docker compose ps` → `Up (healthy)` |
| Backend | Spring Boot 4.1.1 / Java 21 sobe no perfil `dev` contra o banco do Compose | log `Started SharedCalendarApplication` |
| Migration | `V1__create_app_user.sql` aplicada | `flyway_schema_history` → `success = t` |
| Testes unitários | 5 testes de `UuidV7` | `./mvnw test` → `Tests run: 5, Failures: 0, Errors: 0` |
| Testes de integração | `FoundationIT`, 7 testes contra PostgreSQL 18 real via Testcontainers | `./mvnw verify` → `Tests run: 7, Failures: 0, Errors: 0`, `BUILD SUCCESS` |

## F0-03 — Infraestrutura local: concluído

Verificado em 2026-09-12:

| Critério de aceite | Evidência |
|---|---|
| `docker compose up -d` sobe o PostgreSQL com healthcheck | `docker compose ps` → `Up (healthy)`, `RestartCount 0`, saudável em ~4 s |
| Nomes exclusivos do projeto | container `shared-calendar-postgres`, rede `shared-calendar-network`, volume `shared-calendar-postgres-data` |
| Dados em UTC | `show timezone` → `UTC` |
| Volume na raiz exigida pelo PostgreSQL 18 | `show data_directory` → `/var/lib/postgresql/18/docker` |
| Derrubar e subir preservando o volume | `docker compose down` (sem `-v`) + `up -d` → `flyway_schema_history` e as 11 colunas de `app_user` continuam presentes |
| `.env.example` sem valores reais; `.env` ignorado | `git check-ignore -v .env` |
| Guia de execução local | `docs/local-development.md`, com troubleshooting do erro do PostgreSQL 18 |

Correção necessária para chegar aqui: o volume estava montado em `/var/lib/postgresql/data`, que a imagem
oficial do PostgreSQL 18 recusa. O container entrava em loop `Restarting (1)`. O volume passou a ser
montado em `/var/lib/postgresql`.

## F0-04 — Esqueleto do backend: validado contra o banco, com pendências

Verificado em 2026-09-12, com o backend rodando no perfil `dev` e as variáveis do `.env`:

| Verificação | Comando | Resultado |
|---|---|---|
| Perfil ativo | log de inicialização | `The following 1 profile is active: "dev"` |
| Flyway aplicou a V1 | `select … from flyway_schema_history` via `docker compose exec postgres psql` | versão `1`, `V1__create_app_user.sql`, `success = t` |
| Tabela criada conforme a migration | `\d app_user` via `psql` | 11 colunas, PK, índices únicos `ux_app_user_email` e `ux_app_user_handle` sobre `lower(...)`, checks `ck_app_user_status` e `ck_app_user_public_handle_format` |
| `ddl-auto=validate` aceitou o schema | log de inicialização | aplicação iniciou sem erro de validação |
| Health check | `curl http://localhost:8080/actuator/health` | HTTP 200, `"status":"UP"`, componente `db` (PostgreSQL) `UP` |
| Leitura sem autenticação | `curl` em `GET /`, `GET /api/v1/qualquer-coisa`, `GET /actuator/env` | HTTP **401** |
| Escrita sem autenticação | `POST /api/v1/qualquer-coisa` sem token CSRF | HTTP **403** (ver correção abaixo) |

> **Correção (2026-09-12, F0-06).** Esta tabela registrava `POST → 401` como verificado com `curl`. O
> resultado estava mascarado: o filtro de CSRF recusava o POST com 403, o Tomcat fazia um *error dispatch*
> para `/error`, e esse dispatch exigia autenticação, reescrevendo o 403 como 401. O log DEBUG do Spring
> Security confirmou a sequência. O dispatch de erro foi liberado e o status real agora é 403. O teste de
> integração cobre isso, e foi confirmado que ele falha (`expected: 403 but was: 401`) quando a correção
> é removida.

Duas correções foram necessárias para chegar aqui:

1. **A aplicação não subia.** `application.yml` usava `spring.jackson.serialization.write-dates-as-timestamps`,
   propriedade do Jackson 2. O Spring Boot 4 usa Jackson 3, em que a feature está em `DateTimeFeature`. A
   propriedade correta é `spring.jackson.datatype.datetime.write-dates-as-timestamps`. Executei o Jackson
   3.1.0 isoladamente para confirmar: a feature vem desligada por padrão, e `Instant` é serializado como
   `"2026-09-12T17:00:00Z"`. O ADR-0002 foi corrigido com uma nota.
2. **Requisições não autenticadas recebiam 403, não 401.** Com login por formulário e HTTP Basic desligados,
   não havia *authentication entry point*, e o Spring Security caía no 403. O frontend decidido no ADR-0009
   redireciona ao login ao receber 401, então isso quebraria o fluxo. Foi configurado um entry point que
   responde 401.

### Pendências de F0-04

- **Perfil `test`** separado ainda não existe (critério do roadmap).
- **Teste ArchUnit** da regra de dependência entre módulos (prometido no ADR-0001) ainda não existe. A
  versão da biblioteca precisa ser verificada antes.
- O `GlobalExceptionHandler` existe, mas o formato `application/problem+json` **não foi verificado** com
  uma requisição real. As respostas 401 acima vêm do Spring Security, não dele.
- O log ainda mostra `Using generated security password`. Ela vem do usuário em memória que o Spring Boot
  cria enquanto não existe um `UserDetailsService` próprio. Com formulário e Basic desligados, não serve
  para autenticar nada, e desaparece na Fase 1.
- `application.yml` lê a porta do banco de `DB_PORT` (padrão `5432`), mas o `.env` define `POSTGRES_PORT`.
  Hoje os dois coincidem. Se alguém mudar `POSTGRES_PORT`, o backend não acompanha.
- No log DEBUG, a requisição anônima recusada criava uma sessão HTTP para guardar a URL `/error`
  (`HttpSessionRequestCache`). Com o dispatch de erro liberado, esse caso deixou de passar pela
  autorização. **Não foi verificado** se o request cache padrão ainda cria sessões em respostas 401
  comuns. Isso deve ser revisto na Fase 1, porque um SPA não usa o redirecionamento pós-login que o
  request cache existe para servir.

## F0-06 — Testes mínimos e CI: parcial

Verificado em 2026-09-12:

| Critério de aceite | Situação | Evidência |
|---|---|---|
| Teste de integração que sobe o contexto contra PostgreSQL real e valida as migrations | **Pronto** | `FoundationIT` com Testcontainers e `postgres:18-alpine`: `./mvnw verify` → 7 testes, 0 falhas |
| Teste de componente do frontend | Pendente | depende da F0-05 |
| Workflow do GitHub Actions | Pendente | depende do repositório remoto |

O que o `FoundationIT` cobre:

- o Flyway registra `V1__create_app_user.sql` e nenhuma migration falhou;
- a tabela `app_user` existe;
- `GET /actuator/health` responde 200 e `"status":"UP"`, sem expor `components` a anônimos;
- `GET /`, `GET /api/v1/qualquer-coisa` e `GET /actuator/env` respondem 401;
- `POST` sem token CSRF responde 403, e não um 401 mascarado.

Decisões de implementação:

- **Requisições HTTP reais** (`RANDOM_PORT` + `java.net.http.HttpClient`), e não MockMvc. O MockMvc não tem
  *error dispatch*, então não detectaria a volta do mascaramento do 403.
- **Container como bean com `@ServiceConnection`**, em vez do singleton `static` descrito no ADR-0001. A
  diferença e o critério para revisitar estão registrados no próprio ADR.
- **Surefire × Failsafe:** `*Test` roda em `./mvnw test` sem Docker; `*IT` roda em `./mvnw verify`.
- O teste roda **sem `.env`**, como vai rodar na CI. O `${POSTGRES_PASSWORD}` sem valor padrão no
  `application.yml` não atrapalha, porque o `@ServiceConnection` fornece as credenciais.
- O Testcontainers removeu os próprios containers ao terminar (verificado com `docker ps -a`).

**Prova de que o teste protege contra o defeito:** com a liberação do dispatch de erro removida
temporariamente, `./mvnw verify` falhou em `escritaSemTokenCsrf` com `expected: 403 but was: 401`.
Restaurada a correção, a suíte voltou a passar.

## O que ainda NÃO existe

Nada abaixo está implementado. Não apresentar nenhum destes itens como pronto.

- frontend React/Vite (nem esqueleto);
- qualquer entidade JPA, repositório, endpoint de negócio ou tela;
- autenticação (a `SecurityConfiguration` atual apenas fecha a aplicação);
- teste de componente do frontend (F0-06);
- CI no GitHub Actions (F0-06);
- seed de desenvolvimento;
- instruções para subir o backend em `docs/local-development.md`;
- repositório remoto no GitHub.

## Ambiente verificado

Diagnóstico inicial de 2026-09-09. Detalhes em [`../AGENTS.md`](../AGENTS.md#ambiente-verificado).

Java 21.0.10 · Maven Wrapper 3.9.16 · Node 24.15.0 · npm 11.12.1 · Docker 29.7.2 · Docker Compose v5.5.1 ·
PostgreSQL 18.6 (container) · Git 2.53.0. PostgreSQL e GitHub CLI não estão instalados na máquina e, por
decisão do projeto, não serão.

Docker Desktop funcionando desde 2026-09-12.

## Configuração Git deste repositório

- Branch principal: `main`.
- Identidade configurada **apenas neste repositório** (`.git/config`), não globalmente.
- Autenticação prevista: HTTPS + Git Credential Manager, conta pessoal `thiagojosetj`.
- Remote: **ainda não configurado**. O repositório é local até a fundação estar pronta.

## Correções feitas a partir de verificação

Registradas porque são exatamente o tipo de erro que passaria despercebido:

- `spring-boot-starter-session-jdbc` **não existe**. O artefato real é
  `org.springframework.session:spring-session-jdbc`. Descoberto consultando o Maven Central.
- No Spring Boot 4 os starters mudaram de nome em relação ao 3.x: `spring-boot-starter-webmvc` (não
  `-web`), `spring-boot-starter-security-oauth2-client` (não `-oauth2-client`), o Flyway ganhou starter
  próprio, e os starters de teste são por módulo. Por isso o `pom.xml` veio do Spring Initializr, e não de
  memória.
- O Spring Boot 4 usa **Jackson 3** (pacote `tools.jackson`). Configurações de Jackson 2 podem impedir a
  aplicação de subir. Ver F0-04 acima.
- A imagem oficial do **PostgreSQL 18** exige o volume em `/var/lib/postgresql`. Ver F0-03 acima.
- **Um `curl` pode mentir sobre o status.** Com o *error dispatch* protegido, um 403 de CSRF chegava ao
  cliente como 401. Só a divergência com o teste de integração revelou o problema. Ver F0-04 e F0-06 acima.
- No **Testcontainers 2**, `PostgreSQLContainer` fica em `org.testcontainers.postgresql`. No **Jackson 3**,
  `JsonNode.asText()` deu lugar a `asString()`. Ambos verificados nos jars antes do uso.

## Próximo passo

1. Fechar as pendências pequenas da F0-04: teste ArchUnit, perfil `test` e a divergência
   `DB_PORT`/`POSTGRES_PORT`.
2. F0-05: esqueleto do frontend.
3. Criar o repositório no GitHub e o workflow de CI, para concluir a F0-06.

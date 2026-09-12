# PROJECT_STATUS — Shared Calendar

Estado **real** do repositório. Este documento descreve apenas o que já existe e foi verificado. Planos
ficam em [`ROADMAP.md`](ROADMAP.md).

**Última atualização:** 2026-09-12
**Fase atual:** Fase 0 — Fundação
**Concluído:** F0-01, F0-02, F0-03
**Validado com pendências:** F0-04
**Próximo item:** F0-06 — teste de integração com Testcontainers

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
| Testes unitários | 5 testes de `UuidV7` | relatório Surefire: `tests="5" errors="0" failures="0"` |

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
| Demais endpoints exigem autenticação | `curl` em `GET /`, `GET /api/v1/qualquer-coisa`, `POST /api/v1/qualquer-coisa`, `GET /actuator/env` | HTTP **401** em todos |

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
- **`./mvnw verify`** ainda não foi executado; foi executado `./mvnw test`.
- O `GlobalExceptionHandler` existe, mas o formato `application/problem+json` **não foi verificado** com
  uma requisição real. As respostas 401 acima vêm do Spring Security, não dele.
- O log ainda mostra `Using generated security password`. Ela vem do usuário em memória que o Spring Boot
  cria enquanto não existe um `UserDetailsService` próprio. Com formulário e Basic desligados, não serve
  para autenticar nada, e desaparece na Fase 1.
- `application.yml` lê a porta do banco de `DB_PORT` (padrão `5432`), mas o `.env` define `POSTGRES_PORT`.
  Hoje os dois coincidem. Se alguém mudar `POSTGRES_PORT`, o backend não acompanha.

## O que ainda NÃO existe

Nada abaixo está implementado. Não apresentar nenhum destes itens como pronto.

- frontend React/Vite (nem esqueleto);
- qualquer entidade JPA, repositório, endpoint de negócio ou tela;
- autenticação (a `SecurityConfiguration` atual apenas fecha a aplicação);
- teste de integração com Testcontainers (F0-06);
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

## Próximo passo

F0-06: teste de integração com Testcontainers que sobe o contexto Spring contra um PostgreSQL 18 real e
verifica que as migrations aplicam. Esse teste teria detectado a falha de inicialização do Jackson antes da
primeira execução manual.

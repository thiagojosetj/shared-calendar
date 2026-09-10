# PROJECT_STATUS — Shared Calendar

Estado **real** do repositório. Este documento descreve apenas o que já existe e foi verificado. Planos
ficam em [`ROADMAP.md`](ROADMAP.md).

**Última atualização:** 2026-09-09
**Fase atual:** Fase 0 — Fundação
**Item em andamento:** F0-04 — Esqueleto do backend (parcial; validação contra o banco bloqueada)

---

## O que já existe

| Área | Situação | Verificado como |
|---|---|---|
| Documentação de produto | `PROJECT_SPEC.md` com regras `RN-*`, glossário e casos de uso | leitura |
| Regras de trabalho | `AGENTS.md` com ambiente verificado e convenções | leitura |
| Roadmap | `docs/ROADMAP.md` com fases, itens e critérios de aceite | leitura |
| ADRs | Nove decisões aceitas, indexadas em `docs/DECISIONS.md` (F0-02 concluído) | leitura |
| Docker Compose | `docker-compose.yml` com PostgreSQL 18, healthcheck, rede/volume/container próprios | `docker compose config` passou — **ainda não foi executado** |
| Variáveis de ambiente | `.env.example` versionado com placeholders; `.env` local ignorado pelo Git | `git check-ignore` |
| Backend | Spring Boot 4.1.1 / Java 21, compila e empacota | `./mvnw -B test` → BUILD SUCCESS |
| Testes unitários | 5 testes de `UuidV7` | `Tests run: 5, Failures: 0, Errors: 0` |
| Migration | `V1__create_app_user.sql` escrita | **ainda não aplicada** — depende do banco |

### Detalhe do backend

- Pacote raiz `io.github.thiagojosetj.sharedcalendar`.
- `config/ClockConfiguration` — `Clock` injetável em UTC.
- `config/SecurityConfiguration` — apenas o health check é público; todo o resto exige autenticação.
- `shared/error/GlobalExceptionHandler` — erros do Spring MVC como `application/problem+json`.
- `shared/id/UuidV7` — geração de identificadores UUID v7 (RFC 9562), com testes.
- `application.yml` — `ddl-auto=validate`, `open-in-view=false`, JDBC e Jackson em UTC/ISO-8601.

## O que ainda NÃO existe

Nada abaixo está implementado. Não apresentar nenhum destes itens como pronto.

- frontend React/Vite (nem esqueleto);
- qualquer entidade JPA, repositório, endpoint de negócio ou tela;
- autenticação (a `SecurityConfiguration` atual apenas fecha a aplicação);
- teste de integração com Testcontainers (F0-06);
- CI no GitHub Actions (F0-06);
- seed de desenvolvimento;
- repositório remoto no GitHub.

## Bloqueio atual

**O daemon do Docker não está disponível nesta máquina no momento.**

Diagnóstico de 2026-09-09:

- os processos `Docker Desktop.exe` e `com.docker.backend.exe` estão em execução;
- o serviço Windows `com.docker.service` está **Stopped**, com tipo de inicialização **Manual**;
- a distro WSL `docker-desktop` está **Stopped**;
- `docker info` falha com `failed to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine`.

Iniciar esse serviço exige elevação de administrador e é uma alteração no sistema, então depende de uma
ação do usuário na janela do Docker Desktop.

**O que fica bloqueado até isso ser resolvido:** subir o PostgreSQL, aplicar a migration `V1`, validar o
`docker-compose.yml` em execução, e escrever/rodar o teste de integração com Testcontainers.

## Ambiente verificado

Diagnóstico de 2026-09-09. Detalhes em [`../AGENTS.md`](../AGENTS.md#ambiente-verificado).

Java 21.0.10 · Maven Wrapper 3.9.16 · Node 24.15.0 · npm 11.12.1 · Docker 29.7.2 (daemon parado) ·
Docker Compose v5.5.1 · Git 2.53.0. PostgreSQL e GitHub CLI não instalados localmente — por decisão, não
serão instalados.

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
  `-web`), `spring-boot-starter-security-oauth2-client` (não `-oauth2-client`), Flyway ganhou starter
  próprio, e os starters de teste são por módulo (`spring-boot-starter-webmvc-test`) em vez de um
  `spring-boot-starter-test` único. Por isso o `pom.xml` veio do Spring Initializr, e não de memória.

## Próximo passo

1. Destravar o Docker (ação do usuário) e então: `docker compose up -d`, aplicar a `V1` e confirmar o
   health check respondendo contra o banco real.
2. F0-06: teste de integração com Testcontainers validando que as migrations aplicam.
3. F0-05: esqueleto do frontend React + Vite + TypeScript.

# PROJECT_STATUS — Shared Calendar

Estado **real** do repositório. Este documento descreve apenas o que já existe e foi verificado. Planos
ficam em [`ROADMAP.md`](ROADMAP.md).

**Última atualização:** 2026-09-09
**Fase atual:** Fase 0 — Fundação
**Item em andamento:** F0-03 — Infraestrutura local com Docker Compose

---

## O que já existe

| Área | Situação |
|---|---|
| Documentação de produto | `PROJECT_SPEC.md` com regras `RN-*`, glossário e casos de uso |
| Regras de trabalho | `AGENTS.md` com ambiente verificado e convenções |
| Roadmap | `docs/ROADMAP.md` com fases, itens e critérios de aceite |
| README | Descreve o problema, a proposta e o status honesto |
| Git | Repositório inicializado, branch `main`, identidade configurada **localmente** |
| `.gitignore` / `.gitattributes` | Cobrem secrets, builds, IDE e normalização de fim de linha |
| ADRs | Nove decisões arquiteturais aceitas e indexadas em `docs/DECISIONS.md` (item F0-02 concluído) |

## O que ainda NÃO existe

Nada abaixo está implementado. Não apresentar nenhum destes itens como pronto.

- backend Spring Boot (nem esqueleto);
- frontend React/Vite (nem esqueleto);
- `docker-compose.yml` e banco em container;
- migrations Flyway;
- `.env.example`;
- qualquer entidade, endpoint, tela ou teste;
- CI;
- repositório remoto no GitHub.

## Ambiente verificado

Diagnóstico de 2026-09-09. Detalhes em [`../AGENTS.md`](../AGENTS.md#ambiente-verificado).

Java 21.0.10 · Maven 3.9.14 · Node 24.15.0 · npm 11.12.1 · Docker 29.7.2 · Docker Compose v5.5.1 ·
Git 2.53.0. PostgreSQL e GitHub CLI não instalados localmente — por decisão, não serão instalados.

## Configuração Git deste repositório

- Branch principal: `main`.
- Identidade configurada **apenas neste repositório** (`.git/config`), não globalmente.
- Autenticação prevista: HTTPS + Git Credential Manager, conta pessoal `thiagojosetj`.
- Remote: **ainda não configurado**. O repositório é local até a fundação estar pronta.

## Histórico de commits relevantes

Preenchido conforme o projeto avança.

## Próximo passo

F0-03: `docker-compose.yml` com PostgreSQL (rede, volume e container com nomes próprios do projeto),
`.env.example` e `docs/local-development.md`. Requer o Docker Desktop aberto para validação real.

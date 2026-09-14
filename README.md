# Shared Calendar

[![CI](https://github.com/thiagojosetj/shared-calendar/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/thiagojosetj/shared-calendar/actions/workflows/ci.yml)
[![Licença: MIT](https://img.shields.io/badge/licen%C3%A7a-MIT-blue.svg)](LICENSE)

Plataforma web colaborativa de agenda e calendários compartilhados, para pessoas e grupos.

> **Status: Fase 0 — Fundação concluída.**
> O repositório tem especificação, arquitetura decidida em ADRs, banco em container, backend e frontend
> executáveis, testes automatizados e workflow de integração contínua. As funcionalidades de produto começam
> na Fase 1 (autenticação). O estado detalhado, com o que foi verificado e como, está em
> [`docs/PROJECT_STATUS.md`](docs/PROJECT_STATUS.md).

---

## O problema

Grupos de pessoas — família, turma da faculdade, colegas de academia, um time de trabalho, os amigos de um
projeto — coordenam compromissos por mensagens soltas. A informação se perde no meio da conversa, ninguém
sabe ao certo quem confirmou presença, e descobrir um horário que sirva para todo mundo vira uma
negociação manual de ida e volta.

Ferramentas de calendário existentes resolvem o calendário individual muito bem, mas tratam o
**compartilhamento entre grupos** como um recurso secundário: ou você compartilha o calendário inteiro, ou
não compartilha nada.

## A proposta

Um calendário onde o **grupo** é cidadão de primeira classe:

- cada pessoa tem seu calendário pessoal privado e participa de quantos grupos quiser;
- cada grupo tem seus próprios membros, papéis, permissões e calendário;
- um mesmo evento pode aparecer em **mais de um grupo sem ser duplicado** — a apresentação do TCC aparece
  em "Faculdade" e em "Startup", mas continua sendo um único evento;
- participantes confirmam presença (Vou / Talvez / Não vou) e o organizador acompanha as respostas;
- o sistema encontra janelas de horário livre entre várias pessoas — **sem revelar o conteúdo dos
  compromissos delas**. Você descobre que a pessoa está ocupada às 15h; você não descobre que é uma
  consulta médica.

Essa última regra é a espinha dorsal do projeto: **disponibilidade e conteúdo são informações separadas, e
essa separação é imposta no backend**, não escondida na interface.

## Por que este projeto existe

É um projeto pessoal de portfólio, construído para exercitar e demonstrar, em um domínio realista:

A tabela mostra onde cada tema vai aparecer no produto e em qual fase do roadmap ele entra. Só a fundação
(Fase 0) está implementada.

| Tema | Onde vai aparecer no produto | Fase |
|---|---|---|
| Autenticação | e-mail/senha e login com Google (OAuth2) | 1 |
| Autorização e RBAC | quatro papéis por grupo, catálogo de permissões e overrides por membro | 2 |
| Modelagem relacional não trivial | evento pertencente a vários grupos, participantes, recorrência, notas | 3 a 5 |
| Datas e fusos horários | evento de dia inteiro vs. com horário, multi-dia, horário de verão, participantes em fusos diferentes | 3 e 4 |
| Processamento agendado idempotente | lembretes e purga da lixeira sem envio ou remoção duplicada | 5 e 6 |
| Privacidade aplicada no servidor | free/busy sem vazar detalhes; notas privadas; busca que respeita permissões | 4, 5 e 7 |
| Soft delete e auditoria | lixeira com janela de 72h e histórico de ações de negócio | 3 e 5 |
| Infraestrutura reproduzível | PostgreSQL em container, migrations versionadas, testes de integração reais | 0 (pronto) |

O objetivo não é um CRUD de calendário: é um sistema cujas **regras de negócio são a parte interessante**.

## O que já existe

A Fase 0 entrega a fundação, sem funcionalidades de produto ainda:

- **Backend** Spring Boot que sobe contra o PostgreSQL, aplica migrations com Flyway e expõe apenas o health
  check. Leituras sem autenticação respondem 401 e escritas sem token CSRF, 403. Visitantes anônimos não
  criam sessão, e erros saem em
  `application/problem+json` sem vazar detalhe interno.
- **Frontend** com o layout base e o menu principal (Início, Calendário, Grupos e Notas), responsivo, com
  tema claro e escuro e contraste WCAG AA. A página inicial mostra se a API está acessível.
- **Regras de arquitetura verificadas por teste:** separação entre módulos, proibição de `LocalDateTime` e
  horário atual sempre vindo de um `Clock` injetável.
- **Integração contínua** com os dois lados do projeto.

## Tecnologias

Versões efetivamente em uso no repositório.

| Camada | Tecnologias |
|---|---|
| **Backend** | Java 21 · Spring Boot 4.1.1 (Spring Framework 7, Spring Security 7.1) · Spring Data JPA · Flyway · Jackson 3 |
| **Banco** | PostgreSQL 18 |
| **Frontend** | React 19.2 · TypeScript 6.0 · Vite 8.3 · React Router 8 · TanStack Query 5 · axios · CSS Modules |
| **Testes** | JUnit · Testcontainers 2 · ArchUnit 1.5 · MockMvc · Vitest 5 · Testing Library |
| **Qualidade** | oxlint (com regras de acessibilidade) · Prettier |
| **Infraestrutura** | Docker · Docker Compose · GitHub Actions |

## Como executar

Pré-requisitos: **JDK 21**, **Node.js 20.19+ ou 22.12+** e **Docker Desktop** aberto. Maven e PostgreSQL
não precisam estar instalados. O passo a passo completo, com troubleshooting, está em
[`docs/local-development.md`](docs/local-development.md).

**1. Configuração e banco** (na raiz do repositório):

```bash
cp .env.example .env
```

```bash
docker compose up -d
```

**2. Backend** (na pasta `backend/`, no Git Bash, Linux ou macOS; no PowerShell use
`.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"`):

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

**3. Frontend** (na pasta `frontend/`):

```bash
npm ci
```

```bash
npm run dev
```

Abra `http://localhost:5173`. A página inicial deve mostrar "A API está disponível."

## Testes

| Onde | Comando | O que roda |
|---|---|---|
| `backend/` | `./mvnw test` | testes unitários, de camada web e regras de arquitetura (sem Docker) |
| `backend/` | `./mvnw verify` | tudo acima + testes de integração contra PostgreSQL real (com Docker) |
| `frontend/` | `npm run test` | testes de componentes, hooks e cliente HTTP |
| `frontend/` | `npm run lint` · `npm run typecheck` · `npm run format:check` | qualidade estática |

Os testes de regra crítica foram conferidos com **mutação**: o defeito é introduzido de propósito, o teste
precisa falhar, e a correção é restaurada.

## Estrutura do repositório

```text
shared-calendar/
├── backend/                 aplicação Spring Boot (monólito modular por domínio)
│   └── src/main/java/io/github/thiagojosetj/sharedcalendar/
│       ├── config/          segurança, relógio e configuração transversal
│       └── shared/          erros e identificadores usados por todos os módulos
├── frontend/                SPA React
│   └── src/
│       ├── app/             rotas, layout e providers
│       ├── features/        uma pasta por área do produto
│       └── shared/          cliente HTTP, componentes de UI e estilos
├── docs/                    roadmap, status, decisões e guia local
│   └── adr/                 decisões arquiteturais
├── .github/workflows/       integração contínua
├── docker-compose.yml
└── PROJECT_SPEC.md          regras de negócio numeradas (RN-*)
```

## Decisões de arquitetura

As escolhas caras de reverter estão registradas em ADRs, cada uma com as alternativas consideradas:

| ADR | Decisão |
|---|---|
| [0001](docs/adr/0001-estrutura-do-repositorio-e-arquitetura-do-backend.md) | Monorepo, módulos por domínio verificados por ArchUnit, UUID v7 |
| [0002](docs/adr/0002-datas-horas-e-fuso-horario.md) | `Instant` para momentos, `LocalDate` para dia inteiro, `Clock` injetável |
| [0003](docs/adr/0003-autenticacao-e-sessao.md) | Sessão server-side com cookie HttpOnly em vez de JWT |
| [0004](docs/adr/0004-modelo-de-autorizacao-rbac.md) | Papéis fixos + overrides resolvidos em um único ponto |
| [0005](docs/adr/0005-eventos-em-multiplos-grupos.md) | Evento em vários grupos sem duplicação, autoridade ancorada na autoria |
| [0006](docs/adr/0006-soft-delete-lixeira-e-auditoria.md) | Lixeira de 72h e audit log que sobrevive à purga |
| [0007](docs/adr/0007-disponibilidade-e-privacidade.md) | Free/busy cujo tipo não carrega conteúdo de evento |
| [0008](docs/adr/0008-modelo-de-eventos-recorrentes.md) | `RRULE` do RFC 5545 com expansão sob demanda |
| [0009](docs/adr/0009-plataforma-do-frontend.md) | FullCalendar (MIT) atrás de adaptador, CSS Modules, oxlint |

## Documentação

| Documento | Conteúdo |
|---|---|
| [`PROJECT_SPEC.md`](PROJECT_SPEC.md) | Regras de negócio numeradas (`RN-*`), glossário e casos de uso de referência |
| [`docs/ROADMAP.md`](docs/ROADMAP.md) | Fases, entregas e critérios de aceite |
| [`docs/DECISIONS.md`](docs/DECISIONS.md) | Índice das decisões técnicas |
| [`docs/PROJECT_STATUS.md`](docs/PROJECT_STATUS.md) | O que já existe de fato e como foi verificado |
| [`docs/local-development.md`](docs/local-development.md) | Execução local passo a passo e troubleshooting |

## Roadmap resumido

| Fase | Escopo | Situação |
|---|---|---|
| 0 | Fundação: documentação, arquitetura, Docker, PostgreSQL, backend, frontend, testes e CI | Concluída |
| 1 | Autenticação, perfil e calendário pessoal | Próxima |
| 2 | Grupos, papéis, permissões e convites | Planejada |
| 3 | Eventos, participantes, RSVP e visualizações de calendário | Planejada |
| 4 | Calendário avançado: recorrência, drag-and-drop, conflitos, pesquisa | Planejada |
| 5 | Colaboração: notas, comentários, checklists e auditoria | Planejada |
| 6 | Notificações, lembretes agendados, Web Push e tempo real | Planejada |
| 7 | Disponibilidade e busca de horários em comum | Planejada |
| 8 | Exportação/importação `.ics` e integrações externas | Planejada |
| 9 | PWA, mobile, IA e apresentação de portfólio | Planejada |


O detalhamento com critérios de aceite está em [`docs/ROADMAP.md`](docs/ROADMAP.md).

## Licença

Distribuído sob a licença MIT. Veja [`LICENSE`](LICENSE).

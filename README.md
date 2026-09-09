# Shared Calendar

Plataforma web colaborativa de agenda e calendários compartilhados, para pessoas e grupos.

> **Status: Fase 0 — Fundação (em andamento).**
> Neste momento o repositório contém especificação, arquitetura e documentação. O código da aplicação
> ainda está sendo construído. Esta seção é atualizada a cada incremento — veja
> [`docs/PROJECT_STATUS.md`](docs/PROJECT_STATUS.md) para o estado real e detalhado.

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

| Tema | Onde aparece no produto |
|---|---|
| Autenticação | e-mail/senha e login com Google (OAuth2) |
| Autorização e RBAC | quatro papéis por grupo, catálogo de permissões e overrides por membro |
| Modelagem relacional não trivial | evento pertencente a vários grupos, participantes, recorrência, notas |
| Datas e fusos horários | evento de dia inteiro vs. com horário, multi-dia, horário de verão, participantes em fusos diferentes |
| Processamento agendado idempotente | lembretes e purga da lixeira sem envio ou remoção duplicada |
| Privacidade aplicada no servidor | free/busy sem vazar detalhes; notas privadas; busca que respeita permissões |
| Soft delete e auditoria | lixeira com janela de 72h e histórico de ações de negócio |
| Infraestrutura reproduzível | PostgreSQL em container, migrations versionadas, testes de integração reais |

O objetivo não é um CRUD de calendário: é um sistema cujas **regras de negócio são a parte interessante**.

## Documentação

| Documento | Conteúdo |
|---|---|
| [`PROJECT_SPEC.md`](PROJECT_SPEC.md) | Regras de negócio numeradas (`RN-*`), glossário e casos de uso de referência |
| [`AGENTS.md`](AGENTS.md) | Como o trabalho é conduzido neste repositório |
| [`docs/ROADMAP.md`](docs/ROADMAP.md) | Fases, entregas e critérios de aceite |
| [`docs/DECISIONS.md`](docs/DECISIONS.md) | Índice das decisões técnicas |
| [`docs/adr/`](docs/adr/) | Decisões arquiteturais detalhadas |
| [`docs/PROJECT_STATUS.md`](docs/PROJECT_STATUS.md) | O que já existe de fato |
| [`docs/local-development.md`](docs/local-development.md) | Como executar o projeto localmente |

## Tecnologias

A stack está definida e as decisões estão registradas nos ADRs. As versões exatas passam a valer conforme
cada parte é efetivamente construída — o [`PROJECT_STATUS.md`](docs/PROJECT_STATUS.md) sempre reflete o que
já está no repositório.

**Backend:** Java 21 · Spring Boot · Spring Web · Spring Security · Spring Data JPA · Bean Validation ·
Flyway · JUnit · Testcontainers
**Banco:** PostgreSQL
**Frontend:** React · Vite · TypeScript
**Infraestrutura:** Docker · Docker Compose

## Como executar

As instruções completas ficam em [`docs/local-development.md`](docs/local-development.md) e são escritas
para quem está partindo de uma máquina limpa.

Enquanto a Fase 0 não termina, ainda não existe aplicação executável. Assim que o primeiro fluxo vertical
estiver de pé, esta seção passa a trazer os comandos reais, já testados.

## Roadmap resumido

| Fase | Escopo | Situação |
|---|---|---|
| 0 | Fundação: documentação, arquitetura, Docker, PostgreSQL, esqueleto de backend e frontend, testes mínimos | Em andamento |
| 1 | Autenticação, perfil e calendário pessoal | Planejada |
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

A definir antes da publicação do repositório como público.

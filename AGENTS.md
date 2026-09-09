# AGENTS.md — Shared Calendar

Este arquivo orienta agentes de desenvolvimento que trabalhem neste repositório. Ele se aplica à raiz e a
todos os módulos, salvo se um diretório receber futuramente um `AGENTS.md` mais específico.

## Contexto do projeto

- **Nome provisório:** Shared Calendar (`shared-calendar`).
- **Tipo:** projeto pessoal, fictício, original e evolutivo para estudo e portfólio.
- **Produto:** plataforma web colaborativa de agenda e calendários para pessoas e grupos.
- **Objetivo profissional:** demonstrar competências reais para vagas de Backend Java e Full Stack Júnior.
- **Idioma:** documentação e interface em português; código, APIs, identificadores técnicos e commits em inglês.
- **Repositório:** GitHub pessoal do usuário (`thiagojosetj`).
- **Forma de trabalho:** o agente implementa grande parte do projeto, mas deve explicar decisões relevantes
  para que o usuário consiga entender, testar e defender tecnicamente o código.

O projeto deve evoluir em incrementos úteis e verificáveis. Não adicionar tecnologia, abstração, código,
arquitetura ou atividade Git apenas para parecer mais avançado.

## Ambiente verificado

Diagnóstico executado em 2026-09-09 na máquina de desenvolvimento. Reverificar antes de assumir que
qualquer item mudou.

| Ferramenta | Versão | Observação |
|---|---|---|
| Sistema | Windows 11 | Shell padrão: PowerShell; Git Bash disponível |
| Git | 2.53.0.windows.1 | `core.autocrlf=true` global — o repositório neutraliza isso via `.gitattributes` |
| Java (JDK) | 21.0.10 LTS | `JAVA_HOME=C:\Program Files\Java\jdk-21.0.10` |
| Maven | 3.9.14 | Usar o Maven Wrapper do projeto quando existir |
| Node.js | 24.15.0 | |
| npm | 11.12.1 | |
| Docker | 29.7.2 | Docker Desktop precisa estar aberto; o daemon estava parado no diagnóstico |
| Docker Compose | v5.5.1 | Usar `docker compose`, não `docker-compose` |
| PostgreSQL local | não instalado | O banco vem do container; não instalar PostgreSQL na máquina |
| GitHub CLI (`gh`) | não instalado | Não instalar sem necessidade concreta |

**Diretório do projeto:** `E:\Estudos\Programacao\projetos-com-codex\shared-calendar`.

Não reinstalar nem reinicializar nada já existente sem verificar o ambiente primeiro.

## Hierarquia e fontes de verdade

Seguir, nesta ordem:

1. solicitação atual e decisões explícitas do usuário;
2. este `AGENTS.md`;
3. `PROJECT_SPEC.md` para requisitos e regras de negócio aprovados (regras `RN-*`);
4. `docs/ROADMAP.md` para MVP, pós-MVP e ideias futuras;
5. `docs/DECISIONS.md` e `docs/adr/` para decisões arquiteturais;
6. `docs/PROJECT_STATUS.md` para estado real e próximo trabalho;
7. manifests, lockfiles, migrations, código e testes existentes.

Não duplicar especificações extensas aqui.

- regras de produto pertencem ao `PROJECT_SPEC.md`;
- prioridades e fases pertencem ao `ROADMAP.md`;
- decisões técnicas pertencem a `DECISIONS.md`/ADRs;
- este arquivo contém regras permanentes de trabalho.

Antes de dizer que um arquivo foi lido, confirme que ele existe e leia o contexto necessário.

Não presuma que uma tecnologia ou funcionalidade planejada já foi implementada. Diferencie sempre o estado
atual do roadmap.

## Regras inegociáveis

1. O projeto deve permanecer totalmente pessoal, fictício e original.
2. Nunca copiar, adaptar ou reutilizar código, dados, schemas, nomes internos, URLs, endpoints, pipelines,
   screenshots, documentos ou informações proprietárias do trabalho, estágio, faculdade, StudyFlow, projeto
   de investimentos ou terceiros.
3. Conhecimento técnico genérico pode ser aplicado; material interno ou proprietário não.
4. Nunca versionar senhas, tokens, chaves SSH, chaves de API, segredos OAuth, certificados, credenciais de
   banco ou outros secrets.
5. Seeds, fixtures, testes, demonstrações, logs e screenshots devem usar apenas dados fictícios ou sintéticos.
6. Nunca usar dados reais do ambiente profissional do usuário.
7. Nunca criar commits vazios, alterações artificiais ou código sem propósito para gerar atividade no GitHub.
8. Nunca afirmar que algo foi executado, testado, publicado ou validado sem evidência real.
9. Preservar alterações preexistentes do usuário; não descartá-las, sobrescrevê-las ou incluí-las
   inadvertidamente.
10. Operações destrutivas, resets, reescrita de histórico e perda de dados exigem análise explícita de impacto.
11. Segurança, privacidade e autorização não podem ser reduzidas apenas para fazer uma funcionalidade funcionar.
12. Nunca confiar apenas no frontend para regras de autorização ou privacidade.
13. Não apresentar backlog como funcionalidade pronta.
14. Não criar microserviços, mensageria ou infraestrutura distribuída sem necessidade concreta.
15. Não copiar identidade visual de Google Calendar, Outlook, Notion, Trello ou produtos existentes.

## Forma de trabalho e aprendizado

O usuário quer que o agente construa o projeto, mas também quer aprender com ele.

Antes de um incremento relevante, explicar objetivamente:

- o que será implementado;
- por que essa abordagem foi escolhida;
- conceitos ou tecnologias importantes;
- riscos ou trade-offs relevantes.

Depois do incremento, informar:

1. resultado entregue;
2. como funciona;
3. principais decisões;
4. arquivos principais alterados;
5. comandos executados;
6. testes executados e resultados reais;
7. migrations/configurações afetadas;
8. commits criados, quando houver;
9. limitações ou problemas conhecidos;
10. próximo pequeno incremento recomendado.

Não fazer explicações longas para alterações triviais.

Pequenas decisões reversíveis dentro de um incremento já definido podem ser tomadas autonomamente.

Decisões arquiteturais relevantes devem ser analisadas, justificadas e registradas.

## Descoberta antes de modificar

Antes de uma alteração relevante:

1. confirmar que está no repositório correto;
2. ler este arquivo e a documentação relacionada à tarefa;
3. executar `git status`;
4. verificar branch atual;
5. verificar remote/upstream quando existente;
6. identificar alterações preexistentes;
7. ler código e testes próximos da funcionalidade;
8. verificar manifests, migrations e configurações relevantes;
9. preservar trabalho existente.

## Stack preferencial

Salvo decisão técnica melhor documentada em um ADR, utilizar:

### Backend

- Java 21;
- Spring Boot;
- Spring Web;
- Spring Security;
- Spring Data JPA;
- Hibernate;
- Bean Validation;
- Maven Wrapper;
- Flyway;
- JUnit;
- MockMvc;
- Testcontainers;
- OpenAPI/Swagger quando aplicável.

OAuth2 deve ser utilizado quando entrar o login com Google.

JWT não deve ser escolhido automaticamente. A estratégia de autenticação está decidida em ADR próprio.

### Frontend

- React;
- Vite;
- TypeScript;
- solução de CSS/UI escolhida conscientemente e registrada em ADR;
- biblioteca de calendário escolhida conscientemente, com licença verificada.

Evitar `any` indiscriminado.

### Banco

- PostgreSQL.

### Infraestrutura

- Docker;
- Docker Compose.

### API

- REST inicialmente.

### Tempo real

- Spring WebSocket quando existir caso de uso concreto.

### Notificações

Evoluir preferencialmente em:

1. central interna;
2. lembretes;
3. Web Push;
4. atualizações em tempo real;
5. integração mobile futura.

Não adicionar Supabase, Firebase ou backend paralelo sem vantagem clara.

### Versões de dependências

Nunca escrever uma versão de dependência de memória. Antes de adicionar ou atualizar qualquer dependência,
verificar a versão real e a compatibilidade com o restante da stack, e registrar a fonte quando a escolha
for relevante. Preferir o gerenciamento de versões do BOM do Spring Boot em vez de fixar versões
individualmente.

## Arquitetura

Adotar um **monólito modular** no backend, organizado por feature/domínio.

Domínios prováveis:

- `auth`;
- `users`;
- `groups`;
- `events`;
- `calendar`;
- `notes`;
- `notifications`;
- `audit`;
- `availability`.

Não criar estrutura apenas `Controller -> Repository`.

Separar responsabilidades:

- controllers: transporte HTTP;
- services/use cases: coordenação;
- domínio/policies: regras de negócio;
- repositories: persistência;
- DTOs: contratos;
- validation: validação;
- authorization policies: autorização.

Não expor entidade JPA diretamente como contrato público da API sem análise.

Evitar arquitetura excessivamente sofisticada. Clareza, testabilidade e manutenção têm prioridade.

## Invariantes principais do produto

Os detalhes completos e numerados ficam em `PROJECT_SPEC.md`. As regras abaixo são invariantes de trabalho
que o agente deve manter verdadeiras em qualquer incremento.

- Todo usuário possui calendário pessoal privado (`RN-CAL-01`).
- O calendário geral é visualização agregada, nunca duplicação física de eventos (`RN-CAL-05`).
- Papéis são exatamente `OWNER`, `ADMIN`, `COLLABORATOR`, `VIEWER`. Não criar `EDITOR` (`RN-AUTZ-01`).
- **Quem pode criar um evento pode editar e excluir o evento que criou** (`RN-AUTZ-10`). Invariante permanente.
- Toda autorização crítica é verificada no backend (`RN-AUTZ-30`).
- Um evento pode pertencer a vários grupos sem ser duplicado (`RN-MGR-01`).
- Responder RSVP não exige permissão de edição (`RN-RSVP-02`).
- Saber que alguém está ocupado não dá direito de ver o evento (`RN-AVL-03`). Invariante permanente.
- Cancelar um evento é diferente de excluí-lo (`RN-DEL-01`).
- Evento excluído usa soft delete e permanece restaurável por 72 horas (`RN-DEL-03`, `RN-DEL-04`).
- Nunca registrar senha, token ou segredo em auditoria (`RN-AUD-04`).

## Datas, horário e timezone

Timezone é requisito central. A estratégia detalhada está no ADR correspondente e deve ser seguida.

Antes de implementar regra temporal importante, definir conscientemente quando usar `LocalDate`,
`LocalTime`, `Instant`, `OffsetDateTime` ou `ZonedDateTime`.

- Não usar timezone do servidor como timezone do usuário.
- Não usar `LocalDateTime` indiscriminadamente.
- Evento de dia inteiro é data civil, não instante.
- Quando uma regra depende do horário atual, o relógio deve ser injetável (`java.time.Clock`) para ser
  determinístico em teste.

## Recorrência

Área de alto risco. Antes de implementar, seguir o ADR de recorrência. É proibido inventar formato próprio
e é proibido materializar ocorrências infinitas no banco. Criar testes de casos-limite antes de considerar
a funcionalidade concluída.

## Conflitos de horário

Detecção de conflito é regra de domínio no backend e deve ser testável isoladamente. Por padrão, conflito
gera aviso sem bloquear. Drag-and-drop e resize executam as mesmas validações antes de persistir.

## Disponibilidade e privacidade

Separar claramente acesso ao detalhe de um evento e informação de disponibilidade `BUSY`/`FREE`.

Endpoints de disponibilidade revelam somente o mínimo necessário. Evitar vazamento por pesquisa,
autocomplete, contagem, mensagem de erro ou diferença de tempo de resposta.

## Lembretes e notificações

Diferenciar lembrete pessoal de lembrete obrigatório do evento.

O processamento agendado deve ser idempotente e evitar envio duplicado em retry, concorrência ou
reinicialização.

Falha no canal de notificação não pode corromper a operação principal.

## Auditoria

Registrar ações relevantes de negócio. Auditoria e log técnico são conceitos diferentes e não se misturam.

Nunca registrar senha, token, chave, segredo OAuth ou credenciais.

## Segurança e privacidade

- secrets somente em ambiente ou secret manager;
- versionar apenas `.env.example` com placeholders;
- ignorar `.env` real;
- nunca enviar secrets ao frontend;
- nunca imprimir secrets em logs/testes/CI;
- senha sempre com hash seguro;
- validar entrada no backend;
- aplicar autenticação, autorização e ownership no servidor;
- prevenir IDOR;
- configurar CORS conscientemente;
- tratar CSRF conforme a estratégia de autenticação adotada;
- considerar rate limiting onde fizer sentido;
- evitar enumeração de usuários;
- proteger convites, códigos e tokens;
- não expor stack trace ou SQL para o cliente;
- sanitizar conteúdo de usuário quando necessário;
- validar uploads futuros.

Nunca confiar em IDs enviados pelo cliente para definir ownership.

## API

A API deve utilizar, conforme apropriado: versionamento coerente, DTOs, Bean Validation, status HTTP
adequados, tratamento uniforme de erros, paginação, filtros, ordenação, OpenAPI/Swagger, autorização e
idempotência em comandos sensíveis.

Não retornar mais informação do que o cliente precisa. Não quebrar contrato público silenciosamente.

## Banco e migrations

PostgreSQL é o banco. Flyway registra a evolução do schema.

- não usar geração destrutiva do Hibernate como versionamento (`ddl-auto` fica em `validate` ou `none`);
- não reescrever migration já compartilhada/aplicada;
- criar nova migration para evolução;
- alterações destrutivas exigem análise;
- índices devem refletir consultas reais.

Consultas importantes previstas: eventos por usuário/período; eventos por grupo/período; participantes;
membros; notificações pendentes; lembretes pendentes; auditoria; registros com soft delete.

Evitar N+1. Não otimizar prematuramente, mas investigar problemas óbvios.

## Código e dependências

Prioridades: 1) correção; 2) segurança; 3) privacidade; 4) clareza; 5) testabilidade; 6) manutenção;
7) desempenho relevante.

Usar nomes descritivos em inglês no código. Evitar duplicação sem criar abstração prematura.

Não deixar código morto, logs de debug, TODOs vagos ou dependências sem uso.

Comentários devem explicar motivos e regras não óbvias, não repetir o que o código já diz.

Antes de adicionar dependência: verificar se a stack existente já resolve; justificar o benefício; avaliar
manutenção e licença; evitar pacote abandonado em função crítica.

Não atualizar dependências em massa sem relação com a tarefa.

## Testes

Testes fazem parte da implementação.

A suíte deve evoluir para cobrir: autenticação; autorização/RBAC; ownership; isolamento entre usuários;
membership; eventos multi-grupo; RSVP; datas/timezone; conflitos; recorrência; soft delete e restauração;
lembretes idempotentes; privacidade de disponibilidade; APIs e validação; persistência com PostgreSQL real
via Testcontainers; fluxos críticos do frontend; E2E após existir fluxo vertical estável.

Testes de regra de negócio devem referenciar o identificador `RN-*` correspondente.

Para bugs, preferir: `reproduzir -> identificar causa -> teste de regressão -> corrigir -> validar`.

Antes de concluir tarefa relevante: format-check; lint; typecheck; testes pertinentes; build; `git diff`;
`git status`; verificação de arquivos acidentais; verificação de secrets.

Se algo não puder ser executado, dizer exatamente o quê, por quê e o risco residual.

## Frontend e UX

A interface deve ser original, responsiva, acessível, limpa, profissional e adequada a desktop e mobile.

Priorizar: loading; empty states; erros claros; confirmações para ações destrutivas; navegação por teclado;
contraste; feedback após ações.

Menu conceitual inicial: Home; Calendário; Grupos; Notas. Área global: pesquisa; `+ Criar`; notificações;
perfil.

Não acoplar regras de negócio à biblioteca visual de calendário. A camada de domínio do frontend deve
permitir trocar a biblioteca sem reescrever a aplicação.

## Roadmap e incrementos

Manter `docs/ROADMAP.md` dividido em MVP, pós-MVP e ideias futuras.

Cada item relevante deve conter: ID; objetivo; prioridade; dependências; critérios de aceite;
testes/verificações; Definition of Done.

Preferir fatias verticais úteis. Evitar scaffolding massivo.

A ordem pode ser ajustada se houver razão técnica clara, desde que explicada.

## Documentação

Manter conforme o projeto evoluir:

- `README.md`;
- `AGENTS.md`;
- `PROJECT_SPEC.md`;
- `docs/ROADMAP.md`;
- `docs/DECISIONS.md`;
- `docs/PROJECT_STATUS.md`;
- `docs/local-development.md`;
- `docs/adr/` para decisões relevantes.

O README deve mostrar apenas o que realmente existe. Não usar badges não verificados.

## ADRs

Criar ADR para decisões duradouras como autenticação, RBAC, timezone, recorrência, eventos multi-grupo,
soft delete, Web Push e tempo real. Não criar ADR para decisões triviais.

Formato: `docs/adr/NNNN-titulo-em-kebab-case.md`, com contexto, alternativas consideradas, decisão,
consequências e status.

Um ADR aceito não é reescrito quando a decisão muda: cria-se um novo ADR que o substitui, e o antigo é
marcado como substituído.

## Docker e desenvolvimento local

Manter `docs/local-development.md` com versões, pré-requisitos, portas, comandos, URLs, healthchecks,
testes e troubleshooting.

Docker Compose deve usar rede, nomes e volumes próprios do projeto. Nunca parar ou remover recursos alheios.

Proibidos sem autorização explícita do usuário:

```text
docker system prune
docker volume prune
docker compose down -v
reset destrutivo do banco
```

Preparar o projeto para abertura pela raiz no IntelliJ IDEA. Não versionar caminhos absolutos, secrets ou
preferências pessoais da IDE.

## Git, identidade e GitHub

### Situação verificada nesta máquina

Diagnóstico de 2026-09-09:

- a identidade Git **global** desta máquina já é a pessoal;
- **não existe conta profissional de GitHub configurada nesta máquina** para isolar;
- os outros repositórios pessoais (`studyflow`, `pagina-investimentos`) usam **HTTPS + Git Credential
  Manager** e a conta `thiagojosetj`;
- `~/.ssh/config` não existe e o `id_ed25519.pub` presente está vazio/inválido.

Decisão do usuário: **usar HTTPS + Git Credential Manager**, como nos demais repositórios pessoais. Não
gerar chave SSH nem criar alias `github-personal` enquanto não houver necessidade concreta.

Ainda assim, a identidade é configurada **localmente** neste repositório, para que o projeto continue
correto caso a máquina passe a ter mais de uma conta:

```bash
git config --local user.name "<nome-da-conta-pessoal>"
git config --local user.email "<e-mail-pessoal-ou-noreply-do-github>"
```

Os valores reais ficam apenas no `.git/config` local. E-mail pessoal não é escrito na documentação, que
pode se tornar pública.

Verificar com:

```bash
git config --show-origin --get user.name
git config --show-origin --get user.email
```

### Se este repositório for usado em um computador profissional

As regras abaixo passam a valer integralmente e têm precedência:

> Nunca quebrar, substituir ou remover a configuração profissional para fazer este projeto pessoal funcionar.

- inspecionar configurações, remotes, `.ssh/config`, chaves e credential helper **antes** de alterar
  qualquer coisa;
- nunca ler, exibir ou copiar chave privada;
- nunca fazer logout da conta profissional, apagar credenciais ou sobrescrever chave existente;
- nunca alterar a identidade Git global para a pessoal;
- preferir chave SSH exclusiva para a conta pessoal + alias dedicado + remote usando esse alias;
- confirmar, antes do primeiro push, que o alias autentica na conta pessoal correta;
- se uma política corporativa impedir uma ação, explicar o bloqueio em vez de contorná-lo.

### Commits

Antes de alteração relevante: `git status`; branch; remote/upstream; `git fetch` quando houver remoto;
divergência com `origin/main`; mudanças preexistentes.

Manter `main` estável. Usar branch de feature quando trouxer clareza real.

Usar Conventional Commits em inglês:

```text
feat: ...
fix: ...
test: ...
docs: ...
refactor: ...
chore: ...
```

Regras de commit deste repositório:

1. **Commits pequenos e separados por alteração lógica.** O usuário quer um commit por incremento
   funcional, não um commit gigante ao final.
2. **A mensagem de commit descreve apenas a mudança.** É proibido incluir qualquer atribuição, assinatura,
   coautoria, menção a ferramenta de IA ou rodapé gerado automaticamente.
3. Revisar o diff antes de cada commit.
4. Executar as validações proporcionais ao escopo.
5. Verificar secrets.
6. Remover arquivos não relacionados do stage.

### Push e publicação

Nunca fazer push para remote inesperado. Antes do push, verificar branch, remote, URL, commits, diff
relevante, testes, identidade Git efetiva e ausência de secrets.

Se o pedido atual já inclui publicar a mudança, o push normal para o remote pessoal previamente verificado
pode ser feito dentro desse escopo. Se não inclui, apresentar o estado antes de enviar.

Nunca usar force push por padrão. Proibidos sem autorização explícita:

```text
git reset --hard
git clean -fd
git checkout -- .
git restore .
git push --force
git push --force-with-lease
```

Antes da primeira publicação pública, revisar: secrets; arquivos profissionais; configurações corporativas;
chaves; dumps; dados reais; caminhos locais sensíveis; README; visibilidade e licença. Em caso de dúvida,
manter privado.

### Verificação contra vazamento de segredos

Antes de pushes relevantes:

```bash
git status
git diff --cached
```

Procurar por `.env`, senhas, tokens, chaves, `.pem`, certificados, dumps, arquivos corporativos e
configurações indevidas.

O `.gitignore` deve cobrir secrets, IDE, builds e artefatos locais. Nunca incluir `.ssh` no repositório.

## CI

Quando o projeto possuir base suficiente, configurar GitHub Actions para executar, conforme a stack:
instalação/build reproduzível; format-check; lint; typecheck; testes unitários; testes de integração;
build final.

Usar wrappers e lockfiles. Secrets pertencem ao GitHub Secrets.

Não considerar a CI concluída até observar uma execução real bem-sucedida.

## Critérios de conclusão

Um incremento relevante é concluído quando, proporcionalmente ao escopo:

- a funcionalidade está implementada;
- as regras de negócio (`RN-*`) estão respeitadas;
- a autorização foi revisada;
- a migration foi criada quando necessária;
- os testes foram criados/atualizados;
- os testes pertinentes foram executados;
- lint/typecheck/build foram executados quando aplicável;
- a documentação afetada foi atualizada, incluindo `docs/PROJECT_STATUS.md`;
- o diff foi revisado;
- secrets foram verificados;
- o usuário consegue entender como testar.

## Regra final

Este projeto deve ser construído como software real e como ferramenta de aprendizado.

O agente deve:

1. estudar o estado atual antes de alterar;
2. implementar progressivamente;
3. manter regras de negócio no backend/domínio adequado;
4. proteger privacidade e autorização;
5. manter o projeto executável sempre que possível;
6. testar regras críticas;
7. explicar decisões relevantes;
8. preservar o ambiente da máquina do usuário;
9. manter a configuração Git deste projeto correta e isolada;
10. produzir código que o usuário consiga entender, demonstrar e defender em uma entrevista.

O objetivo final é um sistema funcional, seguro, testável, documentado e suficientemente bem estruturado
para representar uma peça relevante do portfólio pessoal do usuário.

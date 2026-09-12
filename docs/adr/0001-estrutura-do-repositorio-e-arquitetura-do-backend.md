# ADR-0001 — Estrutura do repositório e arquitetura do backend

- **Status:** Aceito
- **Data:** 2026-09-09
- **Regras atendidas:** `RN-AUTZ-24`, `RN-AUTZ-30`, `RN-SEC-05`, `RN-SEC-06`

## Contexto

O projeto tem backend Java e frontend TypeScript, desenvolvidos pela mesma pessoa, evoluindo juntos e
compartilhando o contrato da API. Precisamos decidir, antes da primeira linha de código: como organizar o
repositório, como organizar os pacotes do backend, como impedir que o "monólito modular" vire um monólito
emaranhado, qual estratégia de identificadores usar e como os testes serão estruturados.

Essas decisões são caras de reverter: mudar estratégia de ID depois de ter dados exige migração; mudar a
estrutura de pacotes depois de 40 classes é refatoração de baixo valor.

## Decisão

### 1. Monorepo

```text
shared-calendar/
├── backend/          # aplicação Spring Boot (Maven)
├── frontend/         # SPA React + Vite + TypeScript
├── docs/             # documentação, ADRs
├── docker-compose.yml
├── .env.example
├── AGENTS.md
├── PROJECT_SPEC.md
└── README.md
```

**Alternativas consideradas.** Repositórios separados (backend e frontend) e monorepo com ferramenta de
build unificada (Nx, Turborepo).

Repositórios separados exigiriam versionar e sincronizar o contrato da API entre dois lugares — custo puro
para uma pessoa só. Uma ferramenta de monorepo resolveria orquestração de builds que aqui são apenas dois
comandos. O monorepo simples vence: um `git clone` traz o projeto inteiro, e como peça de portfólio se
apresenta em um único link.

### 2. Coordenadas Maven e pacote raiz

- `groupId`: `io.github.thiagojosetj`
- `artifactId`: `shared-calendar`
- pacote raiz: `io.github.thiagojosetj.sharedcalendar`

**Por quê.** A convenção Java pede um domínio reverso que você controle. Não há domínio próprio; o
namespace `io.github.<usuário>` é o fallback reconhecido e aceito inclusive pelo Maven Central. Usar
`com.sharedcalendar` seria reivindicar um domínio de terceiro.

### 3. Organização por domínio, não por camada técnica

```text
io.github.thiagojosetj.sharedcalendar
├── SharedCalendarApplication.java
├── config/                     # configuração transversal (segurança, CORS, Jackson, Clock, OpenAPI)
├── shared/                     # tipos e utilitários sem dono: ids, erros, paginação, tempo
│   ├── error/                  # exceções de domínio + @RestControllerAdvice
│   ├── id/                     # geração de identificadores
│   └── time/                   # abstrações de tempo
├── users/
│   ├── api/                    # controllers e DTOs (contrato HTTP)
│   ├── application/            # services / casos de uso (coordenação e transação)
│   ├── domain/                 # entidades, value objects, regras, policies
│   └── persistence/            # repositórios Spring Data e queries
├── auth/
├── groups/
├── events/
├── calendar/
├── notes/
├── notifications/
├── audit/
└── availability/
```

Regra de dependência entre módulos:

- `api` → `application` → `domain`; `persistence` implementa o que `domain`/`application` precisam;
- **nenhum módulo importa `api` ou `persistence` de outro módulo.** A comunicação entre domínios passa por
  `application` (um service público do módulo) ou por eventos de domínio;
- `shared` pode ser importado por todos; `shared` não importa nenhum domínio.

### 4. A regra de módulos é verificada por teste, não por disciplina

Usamos **ArchUnit** com um teste que falha se a regra for violada.

**Alternativas consideradas.** Spring Modulith e "só combinar e revisar no code review".

Spring Modulith faz isso e mais (documentação de módulos, eventos transacionais publicados), mas traz um
modelo conceitual próprio e mais uma dependência estrutural para explicar. "Combinar" não sobrevive ao
primeiro dia de pressa. ArchUnit custa uma dependência de teste, cabe em um arquivo, falha no `mvn verify`
e é trivial de demonstrar em entrevista: *"a arquitetura tem teste"*.

Se e quando eventos entre módulos virarem centrais (Fase 6), Spring Modulith volta à mesa em um novo ADR.

### 5. Identificadores: UUID v7

Toda entidade de domínio usa `uuid` (tipo nativo do PostgreSQL, 16 bytes) como chave primária, com valores
**UUID versão 7** (RFC 9562) gerados na aplicação.

**Alternativas consideradas.**

| Opção | Por que não |
|---|---|
| `bigserial` | IDs sequenciais aparecem nas URLs (`/api/v1/events/42`). Além de permitir enumeração, vazam volume de negócio ("o sistema tem 42 eventos"). Este produto tem regra explícita contra enumeração de recursos (`RN-AUTZ-32`). |
| UUID v4 | Resolve a enumeração, mas é aleatório: inserções caem em páginas arbitrárias do índice B-tree, causando fragmentação e piorando escrita e cache conforme a tabela cresce. |
| `bigserial` interno + coluna UUID pública | Funciona e é um padrão real, mas duplica colunas e índices em toda tabela e exige traduzir entre os dois mundos em todo repositório. Complexidade que só se paga em escala que este projeto não tem. |
| UUID v7 | Aleatório o suficiente para não ser adivinhável, mas com os 48 bits mais significativos sendo o timestamp Unix em ms — logo, **monotônico**, preservando a localidade de inserção do B-tree. |

A geração fica em `shared/id/UuidV7.java`: a especificação é curta e o código é pequeno e testável
(versão, variante e monotonicidade sob geração em rajada). Preferimos isso a adicionar uma dependência para
gerar um identificador. O JDK 21 não oferece fábrica de UUID v7.

### 6. Camadas e responsabilidades

| Camada | Responsabilidade | Não faz |
|---|---|---|
| `api` (controller) | Traduzir HTTP ↔ DTO, validar formato com Bean Validation, devolver status | Regra de negócio, acesso a repositório |
| `application` (service) | Orquestrar o caso de uso, abrir transação, chamar policies e repositórios | Decidir regra de negócio sozinho |
| `domain` | Entidades, value objects, invariantes e **policies de autorização** | Conhecer HTTP, JPA ou Spring |
| `persistence` | Repositórios Spring Data, queries, projeções | Regra de negócio |

> **Esclarecimento (2026-09-12).** A tabela diz que o `domain` não conhece JPA, mas as entidades JPA moram
> no `domain`. Decisão pragmática: **anotações de mapeamento JPA (`jakarta.persistence`) são permitidas no
> domínio**, porque separar entidade de domínio e entidade de persistência duplicaria cada classe sem ganho
> nesta escala. O que o domínio não pode conhecer é HTTP (`org.springframework.web`, `jakarta.servlet`) nem
> as camadas `api`, `application` e `persistence`. Essa é a interpretação verificada pelo `ArchitectureTest`.

**Entidades JPA nunca são retornadas pela API.** Todo endpoint devolve um DTO ou uma projeção. Isso evita
vazamento acidental de campos (`RN-SEC-05`), serialização de coleções lazy e acoplamento do contrato
público ao schema.

Mapeamento entidade ↔ DTO é escrito à mão, em métodos estáticos de fábrica no próprio DTO
(`EventResponse.from(event)`). **MapStruct foi rejeitado por ora**: geração de código para mapear campo a
campo em um projeto deste tamanho adiciona um processador de anotações e uma etapa de build para resolver
um problema que ainda não dói. Se o número de mapeamentos crescer, é fácil migrar depois.

### 7. Contrato da API e erros

- Prefixo `/api/v1`. O versionamento existe desde o início para que quebrar contrato depois seja uma
  escolha, não um acidente.
- Erros seguem **RFC 9457 (Problem Details)** usando o `ProblemDetail` nativo do Spring, centralizados em
  um `@RestControllerAdvice` em `shared/error`.
- Nenhuma resposta de erro contém stack trace, SQL ou nome de tabela.
- Paginação com `Pageable` do Spring Data, exposta como `page`, `size` e `sort`.

### 8. Banco e migrations

- PostgreSQL, com o schema versionado **exclusivamente** por Flyway.
- `spring.jpa.hibernate.ddl-auto=validate`. Nunca `update` nem `create-drop`, em nenhum perfil que toque um
  banco persistente.
- Dependências: `flyway-core` **e** `flyway-database-postgresql` — desde o Flyway 10 o suporte a PostgreSQL
  saiu do core e mora em artefato próprio.
- Migrations em `backend/src/main/resources/db/migration`, nomeadas `V<n>__<descricao>.sql`.

### 9. Estratégia de testes

| Tipo | Ferramenta | O que cobre | Velocidade |
|---|---|---|---|
| Unitário de domínio | JUnit puro, sem Spring | Policies, cálculo de disponibilidade, expansão de recorrência, detecção de conflito | ms |
| Persistência | `@DataJpaTest` + Testcontainers | Queries, constraints, índices parciais, soft delete | s |
| API | `@SpringBootTest` + MockMvc + Testcontainers | Autorização ponta a ponta, contrato, validação | s |
| Arquitetura | ArchUnit | Regra de dependência entre módulos | ms |

Testes de persistência e de API rodam contra um **PostgreSQL real** via Testcontainers, nunca H2. Um banco
em memória com dialeto diferente não prova nada sobre índices parciais, `timestamptz` ou constraints
específicas do Postgres — que são exatamente as partes arriscadas deste schema.

Para não pagar o custo de subir um container por classe de teste, usa-se o **padrão de container singleton**:
uma classe base declara o container como `static`, iniciado uma vez por JVM, com as propriedades injetadas
via `@DynamicPropertySource`.

> **Nota de implementação (2026-09-12).** O primeiro teste de integração (F0-06) usa outro mecanismo para o
> mesmo objetivo. O container é declarado como bean em uma `@TestConfiguration` com `@ServiceConnection`, o
> padrão gerado pelo Spring Initializr para o Boot 4.1.1. O Spring Boot lê as credenciais do container
> sozinho, e o container é reaproveitado enquanto o contexto de teste estiver no cache do Spring. Na
> prática, classes com a mesma configuração compartilham um único container. A decisão (PostgreSQL real,
> sem um container por classe) não mudou. **Revisitar** quando surgirem testes de fatia (`@DataJpaTest`),
> que usam outro contexto e subiriam outro container: aí o singleton `static` volta a se justificar.
>
> Testes de integração usam o sufixo `IT` e rodam pelo Failsafe em `./mvnw verify`. Testes unitários
> usam `Test` e rodam pelo Surefire em `./mvnw test`, sem Docker.

## Consequências

**Positivas.**
- A estrutura por domínio deixa óbvio onde uma funcionalidade nova mora.
- A regra de dependência é executável: quebrar a arquitetura quebra o build.
- UUID v7 evita, de graça, a classe inteira de bugs de enumeração de recursos.
- Testes contra Postgres real dão confiança sobre o que de fato roda em produção.

**Negativas e custos aceitos.**
- Pacotes mais profundos e mais arquivos por funcionalidade do que uma estrutura por camada.
- Testcontainers exige Docker rodando para a suíte completa — a suíte de domínio continua rodando sem ele.
- Escrever mapeamentos à mão é repetitivo; assumimos o custo em troca de menos mágica no build.
- UUID ocupa 16 bytes contra 8 do `bigint` e não é legível ao depurar a olho nu.

## Revisitar quando

- Eventos entre módulos virarem o principal meio de integração → reavaliar Spring Modulith.
- O número de mapeamentos DTO crescer a ponto de virar ruído → reavaliar MapStruct.
- Houver necessidade real de mais de uma instância da aplicação → reavaliar o que hoje é assumido como
  processo único (agendamento, rate limiting em memória).

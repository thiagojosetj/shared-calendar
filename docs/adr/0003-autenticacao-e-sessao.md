# ADR-0003 — Autenticação e sessão

- **Status:** Aceito
- **Data:** 2026-09-09
- **Regras atendidas:** `RN-AUTH-01` a `RN-AUTH-10`, `RN-SEC-03`, `RN-SEC-04`, `RN-SEC-09`, `RN-USR-01`, `RN-USR-02`

## Contexto

Um SPA React consome uma API REST Spring Boot, com dois caminhos de login: e-mail/senha e Google (OAuth2).
A escolha do mecanismo de sessão trava decisões difíceis de reverter: como fazer logout de verdade, como
revogar acesso de alguém rebaixado de `ADMIN` para `VIEWER`, como autenticar o handshake do WebSocket
futuro, e onde mora o risco de XSS e CSRF.

O usuário pediu explicitamente para **não** escolher JWT por inércia e para documentar o trade-off.

## Decisão

**Sessão server-side com cookie `HttpOnly`, persistida no PostgreSQL via Spring Session JDBC, com proteção
CSRF por cookie/header, e Google através de `oauth2Login` conduzido pelo backend.**

### Alternativas consideradas

| Alternativa | Por que não foi escolhida |
|---|---|
| **(b) JWT stateless em `Authorization: Bearer`** | Não existe logout real nem revogação. Neste produto papéis e permissões mudam o tempo todo — remover alguém de um grupo ou rebaixá-lo não teria efeito até o token expirar. O conserto padrão (denylist de `jti` consultada a cada request) reintroduz estado no servidor: paga-se o preço da sessão sem receber o benefício. |
| **(c) JWT em cookie `HttpOnly`** | O pior dos dois mundos: o cookie é enviado automaticamente, então CSRF volta a ser necessário — some a única vantagem real do JWT. E continua sem revogação. Se já há estado e CSRF, não sobra argumento para a complexidade de emissão e rotação de chave. |
| **(d) Access token curto + refresh token rotativo** | É a solução correta para múltiplos clientes, e é para onde migrar se o app mobile sair do "futuro" para o roadmap. Hoje é a alternativa com mais código de segurança artesanal (rotação, detecção de reuso, revogação de família, fila de requisições no cliente durante o refresh) para servir requisitos que estão marcados como futuros. |
| **(a) Sessão server-side** ✅ | Revogação imediata (apagar a linha encerra o acesso), nenhuma credencial legível por JavaScript, o handshake do WebSocket futuro já chega autenticado sem código extra, e muito menos código de segurança escrito à mão. |

Nota honesta para a entrevista: o argumento de que "com Bearer o WebSocket é impossível" seria **falso**.
Com STOMP sobre WebSocket dá para enviar o token em um header do frame `CONNECT` e lê-lo em um
`ChannelInterceptor`. O argumento correto é mais modesto: **com cookie o handshake já chega autenticado sem
escrever nada**; com Bearer é preciso código adicional (ou um ticket de uso único, no WebSocket cru).

### 1. Topologia de implantação — decidida aqui, não depois

O mecanismo escolhido depende de SPA e API serem **same-site**. Portanto:

- **Produção:** a aplicação Spring serve o build estático do Vite. Origem única, `SameSite=Lax` funciona,
  sem CORS e sem cookie de terceiro.
- **Desenvolvimento:** o dev server do Vite (`:5173`) faz proxy de `/api` para `:8080`. Do ponto de vista do
  navegador também é origem única.
- CORS fica configurado e testado mesmo assim, com origem explícita e `allowCredentials=true`, para o caso
  de execução cross-origin — nunca com `*`.

O frontend continua sendo um projeto separado, com build próprio; apenas o *artefato* de produção é servido
pelo backend. A alternativa (front na Vercel, API em outro domínio) exigiria `SameSite=None; Secure`, o que
transforma o cookie de sessão em cookie de terceiros — bloqueado por padrão no Safari e cada vez mais
restrito no Chrome. Para um projeto que um recrutador vai abrir em um navegador desconhecido, isso é risco
sem contrapartida.

### 2. Sessão

- Starter `spring-boot-starter-session-jdbc`; tabelas `SPRING_SESSION` e `SPRING_SESSION_ATTRIBUTES` criadas
  por migration Flyway própria (não pelo `spring.session.jdbc.initialize-schema`, que escaparia ao
  versionamento do schema).
- Cookie: `HttpOnly`, `SameSite=Lax`, `Secure` em produção, nome próprio (não `JSESSIONID`).
- **Timeout de 8 horas com renovação por atividade.** Trinta minutos seria hostil a um app de calendário que
  fica aberto o dia todo — o usuário volta do almoço e leva 401 no meio da tela.
- O `principal name` gravado na sessão é **o UUID do usuário nos dois fluxos de login**. Sem isso, quem
  entrou por senha e quem entrou pelo Google gravariam chaves diferentes, e "sair de todos os dispositivos"
  encontraria apenas metade das sessões.

Persistir a sessão no banco dá, de graça: sobrevivência a restart do container, e a possibilidade de listar
e encerrar sessões de um usuário (`FindByIndexNameSessionRepository`). Como o argumento central contra JWT
foi revogação, **suspender um usuário apaga suas sessões na mesma transação** — caso contrário o argumento
não se sustentaria.

### 3. CSRF — três armadilhas que precisam estar no código desde o início

O Spring Security defere a geração do token CSRF: ele só é materializado quando alguém efetivamente lê o
valor. Isso produz três bugs que quebram o caminho feliz inteiro e que, juntos, fariam **todo POST retornar
403**:

1. **Um endpoint que só devolve 204 não emite o cookie.** É preciso *tocar* o token. A solução robusta é um
   filtro global registrado após o `BasicAuthenticationFilter` que chama `csrfToken.getToken()` em toda
   requisição, garantindo que o cookie sempre exista e esteja atualizado.
2. **O token não é reemitido após o login.** A estratégia de autenticação do Spring Security expira o token
   antigo (para evitar fixation) e defere o novo. Sem o filtro acima, logar deixa o cliente sem token válido
   e a primeira escrita depois do login falha.
3. **O axios não envia o header em requisição cross-origin sem `withXSRFToken: true`.** Desde o axios 1.6.2
   (correção do CVE-2023-45857), `withCredentials` sozinho não basta. Com o proxy do Vite o bug fica
   escondido; ele aparece no dia em que o front rodar cross-origin.

Configuração do cliente: `withCredentials: true` **e** `withXSRFToken: true`.

### 4. Senha

- Hash com **BCrypt** (custo 12), via `PasswordEncoderFactories.createDelegatingPasswordEncoder()`, que já
  entrega o prefixo `{bcrypt}` e o caminho de migração para outro algoritmo.
- Coluna `password_hash varchar(255)`, **não** `varchar(100)`. Um hash Argon2id com os parâmetros
  recomendados pela OWASP passa de 100 caracteres; economizar bytes hoje custa um `ALTER TABLE` no dia da
  migração.
- Coluna **anulável**: contas criadas apenas pelo Google não têm senha.

**Conta sem senha não pode virar erro 500.** Comparar uma senha contra um hash nulo lança
`IllegalArgumentException`, que não é uma `AuthenticationException` — ela escapa do fluxo de autenticação e
vira 500. Isso criaria um oráculo perfeito de enumeração: 401 para e-mail inexistente, 500 para e-mail
existente. O `UserDetailsService` trata `password_hash` nulo como credencial inválida, produzindo o mesmo
401 de qualquer outra falha.

### 5. Google (OAuth2) — sem vínculo automático por e-mail

Fluxo Authorization Code conduzido pelo backend (`oauth2Login`), com o `client-secret` apenas em variável de
ambiente.

**Regra de vínculo de identidade na Fase 1: casar exclusivamente por `(provider, subject)`.**

O vínculo automático por e-mail — mesmo exigindo `email_verified` do Google — abre um account takeover na
direção que costuma passar despercebida:

> Um atacante cadastra `vitima@gmail.com` com senha própria. A Fase 1 não tem verificação de e-mail, então
> ninguém confirma nada. Semanas depois a vítima clica em "Entrar com Google"; o e-mail bate, o sistema
> vincula as identidades — e o atacante passa a ter acesso por senha à conta da vítima.

Portanto, se o e-mail do Google já existir em uma conta local, **nada é criado nem vinculado**: o usuário é
redirecionado ao login com uma mensagem pedindo que entre por senha e faça o vínculo em um endpoint
autenticado. O vínculo automático só passa a ser seguro quando existir verificação de e-mail dos dois lados
(`RN-AUTH-11`, pós-MVP).

O usuário autenticado no Spring Security precisa ser **o mesmo tipo nos dois fluxos**. Um serviço OIDC
customizado é obrigado a devolver um `OidcUser` — devolver o principal da aplicação não compila. As duas
saídas válidas são: o principal implementar `OidcUser` além de `UserDetails`, ou um handler de sucesso
substituir a autenticação antes de a sessão ser salva. Adotamos a segunda: mantém o principal simples e
concentra a unificação em um lugar.

### 6. Identificador público do usuário

`RN-USR-02` exige um identificador público único. Ele nasce nesta fase e precisa de regras aqui, ou o
primeiro login com Google falha por violação de `NOT NULL`:

- **gerado pelo sistema** (o usuário pode escolher depois), a partir de um slug do nome + sufixo aleatório,
  com repetição em caso de colisão;
- formato validado: minúsculas, dígitos, hífen, 3 a 30 caracteres;
- **lista de reservados**: `admin`, `api`, `me`, `invite`, `login`, `logout`, `settings`, `support`, `root`,
  `system`, `null`, `undefined` — sem isso, o primeiro usuário a registrar `me` cria um conflito de rota;
- não derivado do e-mail, para não vazá-lo.

### 7. Normalização

E-mail e identificador público são normalizados para minúsculas **na escrita**, e os índices únicos são
criados sobre `lower(...)`. Sem normalizar na escrita, quem se cadastra com `Thiago@Gmail.com` e depois tenta
`thiago@gmail.com` recebe "credenciais inválidas" sem nenhuma pista no log.

### 8. Rate limiting no login

Contadores por e-mail (defesa primária) e por IP (defesa secundária), em memória, com janela deslizante.
Implementação como uma classe concreta — **sem interface com uma única implementação**, que seria a
abstração vazia que o próprio projeto proíbe.

Divisão de responsabilidades explícita, para não incrementar o contador em login bem-sucedido:

- o filtro **apenas consulta** se a tentativa é permitida, antes da autenticação;
- o handler de **falha** incrementa;
- o handler de **sucesso** zera o contador do e-mail.

Em produção atrás de proxy reverso, `server.forward-headers-strategy` precisa estar configurado — sem isso
todos os visitantes compartilham o IP do proxy e vinte tentativas derrubam o login para todo mundo. A
configuração vale **apenas** no perfil de produção: confiar em `X-Forwarded-For` sem proxy na frente permite
que qualquer cliente forje o próprio IP.

O header `Retry-After` precisa entrar em `exposedHeaders` do CORS, senão o navegador o esconde do SPA e a
interface não consegue dizer quanto falta.

### 9. Não confundir resposta genérica com resposta idêntica

`RN-AUTH-06` exige que não se possa descobrir se um e-mail está cadastrado. O que se garante é: mesmo
status, mesmo corpo e ausência de headers discriminantes para senha errada, e-mail inexistente e conta sem
senha. Comparar respostas byte a byte em teste seria frágil (`Date`, `Content-Length`, `Set-Cookie` variam)
sem provar nada a mais.

### 10. Auditoria nesta fase

A Fase 1 emite eventos de autenticação apenas para **log estruturado**. A tabela `audit_log` é definida no
[ADR-0006](0006-soft-delete-lixeira-e-auditoria.md) e criada na fase correspondente — decidir o formato
agora e criar a tabela depois evita que três módulos inventem esquemas diferentes no calor do momento.

Nunca são registrados: senha, hash, cookie de sessão, token, `client-secret` ou código de convite completo.

## Escopo da Fase 1

**P0 (bloqueia o resto):** cadastro, login, logout, `GET /me`, sessão JDBC, CSRF funcionando ponta a ponta,
geração de identificador público, calendário pessoal criado no cadastro.

**P1 (na sequência):** login com Google, endpoint autenticado de vínculo de identidade, rate limiting.

A divisão é deliberada: o risco real deste projeto não é insegurança na Fase 1, é a Fase 1 nunca terminar.

## Consequências

**Positivas.** Revogação imediata e compatível com o modelo de papéis; nenhuma credencial acessível a
JavaScript; WebSocket futuro sem gambiarra; pouco código de segurança próprio.

**Negativas e custos aceitos.** O backend não é stateless (uma consulta de sessão por request); um app
mobile nativo exigirá um segundo mecanismo — provavelmente a alternativa (d), em um novo ADR; produção fica
amarrada a servir o SPA na mesma origem.

## Testes obrigatórios

- Login → `POST` protegido usando **somente** o cookie devolvido pelo login (prova que o token CSRF é
  reemitido).
- Requisição de escrita sem header CSRF → 403.
- E-mail inexistente, senha errada e conta criada só pelo Google produzem a mesma resposta de falha.
- Cadastro com maiúsculas e login com minúsculas funcionam.
- Login com Google cujo e-mail já existe em conta local **não** cria nem vincula nada.
- `sub` conhecido faz login na conta correta.
- Suspender um usuário apaga suas sessões e o acesso cessa imediatamente.
- Identificador público é gerado no login social e colisões são resolvidas.
- Identificadores reservados são rejeitados.
- Nenhuma resposta da API contém `password_hash`.

# ADR-0009 — Plataforma do frontend

- **Status:** Aceito
- **Data:** 2026-09-09
- **Regras atendidas:** `RN-UX-01` a `RN-UX-10`, `RN-AUTZ-30`, `RN-SEC-01`

## Contexto

O SPA precisa: consumir uma API REST autenticada por cookie de sessão, renderizar um calendário com quatro
visualizações e arrastar/redimensionar, ser responsivo e acessível, e ter uma identidade visual **original**
(`RN-UX-10`). O usuário pediu decisão explícita e justificada sobre CSS/UI e sobre a biblioteca de calendário.

## Decisão

### 1. Biblioteca de calendário: FullCalendar, atrás de um adaptador

Verificado em 2026-09-09 na documentação oficial: os plugins **`daygrid`, `timegrid`, `list` e
`interaction`** são MIT. `interaction` é o que fornece **arrastar e redimensionar**. Os plugins premium
(licença comercial) são timeline/resource views e otimização de impressão — **nenhum deles é necessário**
para o que a spec pede (mês, semana, dia, agenda/lista, drag-and-drop, resize).

Portanto FullCalendar atende integralmente o produto sob licença MIT, o que é obrigatório para um
repositório de portfólio público.

**Restrição de arquitetura:** `AGENTS.md` exige não acoplar regra de negócio à biblioteca visual. O
FullCalendar fica encapsulado em um único componente adaptador que traduz entre o modelo do domínio do
frontend e o formato dele. O resto da aplicação nunca importa `@fullcalendar/*`.

Isso importa por dois motivos concretos: o modelo do domínio usa fim **exclusivo** em dia inteiro
(ADR-0002), o que coincide com o FullCalendar mas precisa ser uma conversão explícita e testada; e trocar de
biblioteca (`@schedule-x/react`, `react-big-calendar`) deve ser trocar um arquivo, não reescrever a
aplicação.

### 2. Estilo: CSS Modules + design tokens em CSS custom properties

**Alternativas consideradas.**

| Alternativa | Avaliação |
|---|---|
| **Tailwind CSS** | Rápido, consistente e muito comum no mercado. Mas: não fornece identidade visual (ela ainda tem que ser desenhada); enche o JSX de listas longas de utilitários, o que atrapalha exatamente o objetivo de "código que eu consigo explicar"; e adiciona uma etapa de build e um modelo mental a mais. |
| **Biblioteca de componentes (MUI, shadcn/ui)** | Entrega telas prontas rápido, mas MUI carrega uma identidade visual reconhecível — o oposto de `RN-UX-10` — e shadcn traz Tailwind junto. |
| **vanilla-extract** | CSS tipado em TypeScript, ótimo tecnicamente, mas é mais uma ferramenta de build para justificar num projeto cujo diferencial não é o frontend. |
| **CSS Modules + tokens** ✅ | Nativo do Vite, zero configuração, escopo por arquivo automático. Os tokens em variáveis CSS resolvem tema claro/escuro trocando um bloco de `:root`, e são a mesma mecânica que o FullCalendar usa para ser tematizado. |

Decisão consciente e reversível: nada impede adicionar Tailwind depois, componente a componente. O peso da
escolha é baixo; a justificativa é que o diferencial deste projeto é o domínio e a autorização, e o frontend
deve ser limpo, original e com poucas peças móveis.

A paleta de cores dos calendários (`RN-CAL-07`) é definida como tokens, com contraste verificado contra
WCAG AA — e precisa funcionar nos dois temas, o que é justamente o que os tokens tornam gerenciável.

### 3. Camada de dados: TanStack Query

O produto tem cache natural (calendário do mês, lista de grupos, permissões por grupo), invalidação óbvia
(criar um evento invalida o período visível) e a exigência de tratar carregamento, vazio e erro em toda tela
(`RN-UX-07`).

Escrever isso com `useEffect` + `useState` significa reimplementar cache, deduplicação de requisições,
revalidação e estados de erro — pior e em cada tela. RTK Query resolveria o mesmo, mas traz Redux para um
app cujo estado global é pequeno.

Estado de interface (aba ativa, filtros, calendários marcados) fica em estado local do React, com Context
para as duas coisas realmente globais: a sessão autenticada e a seleção de calendários. Sem Zustand por
enquanto — não há problema que ele resolva aqui hoje.

### 4. Tipos gerados a partir do OpenAPI

Os tipos TypeScript dos DTOs são **gerados** do documento OpenAPI que o backend publica (`openapi-typescript`),
não escritos à mão.

Tipos escritos à mão divergem do backend silenciosamente: o campo é renomeado no Java, o TypeScript continua
compilando, e o bug aparece em runtime. Gerando, uma mudança de contrato quebra o `typecheck` — que é
exatamente onde se quer descobrir.

Isso reforça a decisão do ADR-0001 de nunca expor entidade JPA: o contrato é explícito e estável o
suficiente para gerar tipos a partir dele.

### 5. Autenticação no cliente

Sessão por cookie `HttpOnly` (ADR-0003). Consequências para o frontend:

- o cliente HTTP usa `withCredentials: true` **e** `withXSRFToken: true` — sem o segundo, o header CSRF não
  é enviado em requisição cross-origin e **todo POST retorna 403**;
- nenhum token é guardado em `localStorage` — não há token a guardar;
- 401 em qualquer resposta redireciona para o login, tratado em um único interceptador;
- o dev server do Vite faz proxy de `/api` para o backend, mantendo tudo same-origin em desenvolvimento.

### 6. Permissões na interface não são segurança

O frontend consome `GET /api/v1/me/permissions` (ADR-0004) para decidir o que renderizar. Isso é
**usabilidade**: não mostrar um botão que vai falhar. A autorização real está no backend (`RN-AUTZ-30`), e a
interface precisa continuar correta se a resposta chegar depois ou não chegar.

Nenhuma regra de permissão é reimplementada em TypeScript.

### 7. Estrutura de pastas espelhando o backend

```text
frontend/src/
├── app/          # bootstrap, rotas, providers
├── shared/       # cliente HTTP, tipos gerados, componentes de UI, hooks, tokens de estilo
└── features/
    ├── auth/
    ├── calendar/
    ├── events/
    ├── groups/
    └── notes/
```

Cada feature agrupa componentes, hooks de dados e tipos. Espelhar os domínios do backend faz com que
"onde mexo para mudar X" tenha a mesma resposta dos dois lados.

### 8. Ferramentas e qualidade

- **TypeScript em modo estrito.** `any` só com comentário justificando; verificado por regra de lint.
- **ESLint (flat config) + Prettier**, rodando na CI.
- **Vitest + Testing Library** para componentes e hooks; Playwright fica para quando existir um fluxo
  vertical estável (Fase 3+), conforme `AGENTS.md`.
- Versões exatas são fixadas no momento do scaffold, verificadas então — não escritas de memória agora.

### 9. Acessibilidade como requisito, não como polimento

- foco visível e ordem de tabulação coerente; diálogos com foco preso e retorno ao gatilho;
- ações do calendário alcançáveis por teclado — arrastar com o mouse **não pode ser** o único caminho para
  mover um evento (`RN-UX-09`);
- contraste WCAG AA nos dois temas, incluindo as cores de calendário;
- estados de carregamento, vazio e erro em toda tela que busca dados (`RN-UX-07`);
- confirmação explícita em ações destrutivas, dizendo o que vai acontecer (`RN-UX-08`) — e "excluir" precisa
  deixar claro que vai para a lixeira e é reversível por 72h.

## Consequências

**Positivas.** Licença MIT sem pegadinha. Contrato tipado de ponta a ponta. O calendário é substituível.
Poucas dependências para explicar.

**Negativas.** Escrever CSS à mão é mais lento que utilitários no começo, e a consistência visual passa a
depender da disciplina com os tokens em vez de ser imposta por uma ferramenta. A geração de tipos adiciona
um passo ao fluxo de desenvolvimento (regenerar quando o contrato muda) — mitigado por um script npm e por
rodar na CI.

## Revisitar quando

- O estado global crescer além de sessão e seleção de calendário → reavaliar Zustand.
- A quantidade de CSS repetido indicar necessidade de um sistema de utilitários → reavaliar Tailwind.
- Views de recurso (salas, equipamentos) entrarem no escopo → aí sim FullCalendar Premium ou outra
  biblioteca entram na conversa, com custo de licença explícito.

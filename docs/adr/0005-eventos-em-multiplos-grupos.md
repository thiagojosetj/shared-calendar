# ADR-0005 — Eventos pertencentes a múltiplos grupos

- **Status:** Aceito
- **Data:** 2026-09-09
- **Regras atendidas:** `RN-MGR-01` a `RN-MGR-09`, `RN-RSVP-04`, `RN-RSVP-05`, `RN-CAL-05`

## Contexto

"A apresentação do projeto aparece em Faculdade **e** em Startup, sem virar dois eventos." A modelagem é a
parte fácil. A parte difícil, que a spec pediu explicitamente para modelar e documentar antes de
implementar, é **de onde vem a autoridade** quando um evento vive em vários grupos onde o usuário tem papéis
diferentes.

## Decisão

**Tabela de junção `event_group` com grupos-pares (sem grupo primário), autoridade ancorada na autoria e
agregação por operação.**

### 1. A junção

```sql
CREATE TABLE event_group (
    event_id        uuid        NOT NULL REFERENCES event(id) ON DELETE CASCADE,
    group_id        uuid        NOT NULL REFERENCES calendar_group(id) ON DELETE RESTRICT,
    linked_by_user_id uuid      NOT NULL REFERENCES app_user(id),
    linked_at       timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (event_id, group_id)
);
CREATE INDEX ix_event_group_group ON event_group (group_id);
```

`linked_by_user_id` e `linked_at` não são enfeite: a pergunta "quem trouxe este evento para o meu grupo?" é
real, aparece na auditoria e é a primeira coisa que um administrador quer saber.

`ON DELETE CASCADE` no evento e `ON DELETE RESTRICT` no grupo é intencional: apagar um evento remove seus
vínculos; apagar um grupo com eventos vinculados deve ser uma operação consciente, não um efeito colateral.

### 2. Não existe grupo primário

**Alternativa rejeitada:** marcar um dos grupos como "dono" do evento e ancorar a autoridade nele.

Parece organizado e quebra a regra permanente do produto. Basta o autor ser removido do grupo primário — ou
o grupo primário ser excluído — para que "quem cria pode editar o que criou" (`RN-AUTZ-10`) deixe de valer.
A autoridade precisa de uma âncora **imutável**, e a única disponível é `event.created_by_user_id`.

Os grupos são **pares**. Nenhum tem precedência sobre o outro.

### 3. De onde vem a autoridade

| Operação | Quem pode | Agregação |
|---|---|---|
| **Ver** | criador, participante direto, ou membro com `VIEW_CALENDAR` em algum grupo vinculado | `ANY` |
| **Editar conteúdo** | criador, ou quem tem `EDIT_ANY_EVENT` em **algum** grupo vinculado | `ANY` |
| **Vincular a um grupo** | quem tem `CREATE_EVENT` **naquele** grupo **e** pode editar o evento | ambos os lados |
| **Desvincular de um grupo** | quem tem `EDIT_ANY_EVENT` **naquele** grupo, ou o criador | escopo local |
| **Excluir (soft delete)** | criador, ou quem tem `DELETE_ANY_EVENT` em **todos** os grupos vinculados | `ALL` |

Três dessas linhas merecem justificativa.

**Editar com `ANY`, não `ALL`.** Exigir permissão em todos os grupos cria um vetor de sabotagem: qualquer
pessoa com direito de vincular poderia congelar o evento de outra ao associá-lo a um grupo onde essa pessoa
não é membro.

**Vincular exige poder dos dois lados.** Sem a exigência no lado do evento, um membro de um grupo qualquer
poderia arrastar eventos alheios para dentro do seu grupo. Sem a exigência no lado do grupo, alguém poderia
despejar eventos em grupos dos quais nem participa (`RN-MGR-03`).

**Excluir com `ALL`.** Excluir tira o evento de todos os grupos ao mesmo tempo. Um administrador que só tem
poder no grupo A não deve conseguir remover o evento do grupo B — para isso existe **desvincular**, que é a
operação de escopo local. Essa é a razão de as duas operações serem distintas na API.

### 4. Zero grupos é um estado válido

Um evento sem nenhum vínculo é um evento do calendário pessoal do criador (`RN-MGR-02`). Consequência
direta: **desvincular o último grupo não exclui o evento** — ele volta a ser pessoal (`RN-MGR-05`).

A alternativa (proibir zero grupos, ou excluir automaticamente) transformaria "sair do meu grupo com este
evento" em perda de dados para o criador.

### 5. Visibilidade sem N+1

Um usuário vê um evento se for o criador, participante direto, **ou** membro de um grupo vinculado com
`VIEW_CALENDAR`. Isso é uma união de três caminhos e, ingenuamente, vira uma consulta por evento.

A consulta do calendário resolve tudo de uma vez: parte dos grupos do usuário (uma consulta), e busca os
eventos do período por junção com `event_group` **união** eventos onde ele é participante **união** eventos
criados por ele — em uma única consulta paginada, com os grupos vinculados carregados em lote.

Índices que sustentam isso:

```sql
CREATE INDEX ix_event_period ON event (starts_at, ends_at) WHERE deleted_at IS NULL;
CREATE INDEX ix_event_creator ON event (created_by_user_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_participant_user ON event_participant (user_id);
```

### 6. Participantes não duplicam

`event_participant` tem unicidade em `(event_id, user_id)` — a garantia é do banco, não do código de
aplicação. "Convidar todos do grupo" em dois grupos que se sobrepõem resolve a união dos membros e insere
com `ON CONFLICT DO NOTHING`, produzindo um único convite por pessoa (`RN-MGR-07`).

`RN-RSVP-05` complementa: a lista é resolvida **no momento do convite**. Quem entra no grupo depois não é
convidado retroativamente — o contrário criaria convites surgindo do nada para eventos passados.

### 7. Perder acesso ao grupo não desconvida

Se um usuário sai do grupo A mas era participante direto do evento, ele continua participante e continua
vendo aquele evento específico (`RN-MGR-09`, `RN-GRP-22`). O acesso pelo caminho "membro do grupo" acaba; o
acesso pelo caminho "participante convidado" não.

Isso é intencional: cancelar a participação de alguém em um compromisso já aceito é uma decisão do
organizador, não um efeito colateral de gestão de membros.

## Consequências

**Positivas.** Um único registro de evento, como a spec exige. A regra permanente sobrevive a qualquer
reorganização de grupos. A separação entre desvincular e excluir dá aos administradores uma ação
proporcional ao seu poder.

**Negativas.** Toda checagem de permissão sobre evento precisa carregar a lista de grupos vinculados — o que
torna a variante em lote do `PermissionResolver` (ADR-0004) obrigatória, não opcional. A semântica `ANY`/`ALL`
por operação é sutil e precisa estar documentada onde o desenvolvedor a encontre: nos nomes dos métodos da
`EventPolicy`.

## Testes obrigatórios

- Evento vinculado a dois grupos aparece nos dois calendários e existe **uma** linha em `event`.
- Desvincular do grupo A mantém o evento no grupo B.
- Desvincular o último grupo mantém o evento como pessoal do criador.
- Membro dos dois grupos é convidado uma única vez.
- Autor removido do grupo A ainda edita o próprio evento (regra permanente).
- Admin apenas do grupo A **não** consegue excluir evento vinculado a A e B, mas **consegue** desvincular de A.
- Usuário sem acesso a nenhum grupo vinculado e sem convite recebe 404, não 403.
- Consulta de calendário de um usuário com 10 grupos e 200 eventos executa um número constante de queries.

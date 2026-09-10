# ADR-0004 — Modelo de autorização (RBAC com overrides)

- **Status:** Aceito
- **Data:** 2026-09-09
- **Regras atendidas:** `RN-AUTZ-01` a `RN-AUTZ-06`, `RN-AUTZ-10`, `RN-AUTZ-11`, `RN-AUTZ-20` a `RN-AUTZ-24`, `RN-AUTZ-30` a `RN-AUTZ-32`, `RN-AUTZ-40` a `RN-AUTZ-42`

## Contexto

Este é o coração do produto e a parte mais interessante do portfólio. Quatro papéis fixos por grupo, um
catálogo de permissões granulares, exceções por membro, e — o requisito que quebra as soluções simples — um
evento que pode pertencer a **vários grupos ao mesmo tempo**, em cada um dos quais o usuário pode ter um
papel diferente.

O usuário exige que as regras sejam centralizadas, sem condicionais de permissão espalhadas pelo código.

## Decisão

**Papéis fixos em `enum` + tabela de overrides por membro, resolvidos por um único `PermissionResolver` e
aplicados por objetos de policy no domínio.**

### Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| Tabela de papéis e permissões totalmente dinâmica no banco | Os quatro papéis são uma **decisão de produto** (a spec proíbe até criar `EDITOR`), não um dado operacional. Modelar como dado seria construir um mecanismo de papéis customizados que ninguém pediu. |
| `@PreAuthorize` com SpEL + `PermissionEvaluator` | A pergunta "pode editar?" precisa iterar sobre N escopos e agregar com semântica que **muda conforme a operação**. Espremer isso em expressões SpEL produz string ilegível, não refatorável e não depurável — o oposto de "conseguir defender o código". |
| Só objetos de policy, sem catálogo de permissões | Não implementa `EDIT_ANY_EVENT`/`DELETE_ANY_EVENT` nem overrides, que a spec pede. |
| **Papéis em enum + overrides + policies** ✅ | O resolver devolve um `Set<Permission>` puro e testável; as policies expressam a agregação por escopo em Java legível. |

### 1. Catálogo de permissões

```java
public enum Permission {
    VIEW_CALENDAR, RSVP_EVENT,
    CREATE_EVENT, EDIT_OWN_EVENT, EDIT_ANY_EVENT, DELETE_OWN_EVENT, DELETE_ANY_EVENT,
    MANAGE_MEMBERS, MANAGE_ROLES, MANAGE_GROUP, MANAGE_NOTES, VIEW_AUDIT_LOG
}
```

`VIEW_CALENDAR` e `RSVP_EVENT` não estavam na lista original da spec e foram acrescentados de propósito. Sem
eles, "o `VIEWER` lê" e "responder RSVP não exige permissão de edição" (`RN-RSVP-02`) virariam condicionais
especiais espalhadas — exatamente o que `RN-AUTZ-24` proíbe.

**`TRANSFER_OWNERSHIP` e `DELETE_GROUP` ficam deliberadamente fora deste enum.** Elas não são permissões
concedíveis: são ações exclusivas do `OWNER`, verificadas por um caminho próprio. Mantê-las fora do catálogo
torna **impossível** escalar até dono por meio de um override — que é o vetor de bug mais provável neste tipo
de modelo. Esta decisão ajusta `RN-AUTZ-20`, que as listava junto das demais.

### 2. Papéis

```java
public enum GroupRole {
    VIEWER,        // VIEW_CALENDAR, RSVP_EVENT
    COLLABORATOR,  // VIEWER + CREATE_EVENT, EDIT_OWN_EVENT, DELETE_OWN_EVENT, MANAGE_NOTES
    ADMIN,         // COLLABORATOR + EDIT_ANY_EVENT, DELETE_ANY_EVENT, MANAGE_MEMBERS,
                   //                MANAGE_ROLES, MANAGE_GROUP, VIEW_AUDIT_LOG
    OWNER          // ADMIN + as ações exclusivas de dono (fora do enum Permission)
}
```

### 3. A regra permanente é um invariante verificado no carregamento da classe

`RN-AUTZ-10` — "quem pode criar pode editar e excluir o que criou" — não é uma convenção a lembrar. Um bloco
`static` no enum verifica, para todo papel, que `CREATE_EVENT` implica `EDIT_OWN_EVENT` e `DELETE_OWN_EVENT`,
e lança exceção no carregamento se alguém quebrar isso. **A aplicação não sobe** com a regra violada.

A mesma regra vale para overrides: um `DENY` em `EDIT_OWN_EVENT` para quem tem `CREATE_EVENT` é rejeitado
com 422 na escrita, não silenciosamente aplicado.

### 4. Escopo: o calendário pessoal não é um grupo disfarçado

```java
public sealed interface CalendarScope {
    record Personal(UUID ownerUserId) implements CalendarScope {}
    record Group(UUID groupId)        implements CalendarScope {}
}
```

Modelar o calendário pessoal como "um grupo com um membro" pareceria elegante e criaria uma classe de bugs:
esse pseudo-grupo apareceria em listagens, aceitaria convites, teria transferência de propriedade. Um tipo
selado obriga o compilador a cobrar o tratamento dos dois casos em todo `switch`.

No escopo pessoal, o dono tem todas as permissões e ninguém mais tem nenhuma.

### 5. O resolver

```java
public interface PermissionResolver {
    Set<Permission> effectivePermissions(UUID userId, CalendarScope scope);
    Map<UUID, Set<Permission>> effectivePermissionsByGroup(UUID userId, Collection<UUID> groupIds);
}
```

A variante em lote existe porque a tela do calendário agregado consulta vários grupos de uma vez;
sem ela, seria N+1 garantido.

Resolução: **`DENY` explícito > `GRANT` explícito > padrão do papel**, com uma exceção — **o `OWNER` é imune
a overrides**. Sem essa regra, um `ADMIN` com `MANAGE_MEMBERS` poderia gravar um `DENY` em `MANAGE_GROUP`
para o dono e inutilizar o grupo permanentemente.

Regra complementar, verificada na escrita de overrides: **ninguém concede o que não possui**, e ninguém
promove alguém a um papel igual ou superior ao seu (`RN-AUTZ-06`).

### 6. Policies de domínio

O resolver responde *"o que este usuário pode"*. As policies respondem *"esta ação específica é permitida"*,
que é onde mora a agregação entre escopos:

- `EventPolicy` — criar, editar, excluir, vincular/desvincular grupo, responder RSVP;
- `GroupPolicy` — configurações, ações exclusivas de dono;
- `MembershipPolicy` — adicionar, remover, mudar papel, alterar overrides.

Policies lançam exceção de domínio (não devolvem `boolean`), para que esquecer de checar o retorno não
produza uma falha silenciosa de segurança.

### 7. Agregação entre escopos

Um evento em N grupos exige decidir **como combinar** as respostas. A semântica muda por operação, e essa é
a razão de a lógica estar em Java e não em SpEL:

| Operação | Agregação | Por quê |
|---|---|---|
| Ver o evento | `ANY` | Basta ter acesso por um caminho (`RN-MGR-08`) |
| Editar o evento | autor **ou** `ANY` grupo com `EDIT_ANY_EVENT` | `ALL` criaria sabotagem: bastaria vincular o evento a um grupo onde o autor não é membro para congelá-lo |
| Excluir globalmente | autor **ou** `ALL` grupos com `DELETE_ANY_EVENT` | Excluir tira o evento de todos os grupos; quem só tem poder em um deles deve **desvincular**, não excluir |
| Desvincular de um grupo | permissão **naquele** grupo | Operação local, autoridade local |
| Responder RSVP | `ANY` com `RSVP_EVENT` | Nunca exige permissão de edição |

### 8. Endpoint de permissões para o frontend

`GET /api/v1/me/permissions?groupIds=...` devolve o conjunto efetivo por grupo. Ele existe **apenas** para o
SPA saber o que renderizar, sem replicar a regra em TypeScript. Nenhuma decisão do backend depende do que o
cliente envia — o resolver sempre recalcula a partir do banco (`RN-AUTZ-30`, `RN-AUTZ-31`).

### 9. Como testar a matriz sem explodir em combinações

Um **teste de aprovação**: gera um texto determinístico `papel -> permissões ordenadas` e compara com um
arquivo versionado. Um único teste cobre a matriz inteira e falha de forma legível sempre que alguém
acrescenta uma permissão ou muda um papel sem intenção — o diff do arquivo mostra exatamente o que mudou.

## Detalhes de persistência que são decisões de segurança

- `@Enumerated(EnumType.STRING)` é **obrigatório** em papel, status, permissão e efeito. `ORDINAL`
  corromperia a autorização em silêncio no dia em que alguém reordenasse um enum. É falha de segurança, não
  de mapeamento.
- Tabela `group_member_permission_override(group_id, user_id, permission, effect)` com unicidade na tripla.
- A distinção entre **403 e 404** é decidida em um único lugar no `@RestControllerAdvice`: recurso privado do
  qual o usuário não é membro devolve 404 (não revela existência); membro sem a permissão específica devolve
  403 (`RN-AUTZ-32`).
- Transferência de propriedade e mudanças de papel gravam auditoria **na mesma transação**, para que uma
  falha parcial não produza mudança de poder sem rastro.

## Consequências

**Positivas.** Um único ponto resolve permissões; a regra permanente é impossível de violar sem quebrar o
startup; a matriz inteira tem um teste; escalada de privilégio até dono é estruturalmente impossível.

**Negativas.** Mudar o conjunto de papéis exige deploy, não configuração — aceito, porque é decisão de
produto. A variante em lote do resolver adiciona uma consulta a mais para manter, e é fácil esquecer de
usá-la (mitigado por teste que conta queries).

## Escopo

**Fase 2 (MVP):** papéis, catálogo, resolver, policies, matriz testada, transferência de propriedade.
**Pós-MVP:** tabela de overrides e sua interface (`RN-AUTZ-22`, `RN-AUTZ-23`). O resolver já nasce com o
ponto de extensão, mas a tabela só é criada quando a funcionalidade for construída.

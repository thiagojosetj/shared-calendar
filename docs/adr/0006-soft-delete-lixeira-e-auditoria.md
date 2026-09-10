# ADR-0006 — Soft delete, lixeira de 72 horas e audit log

- **Status:** Aceito
- **Data:** 2026-09-09
- **Regras atendidas:** `RN-DEL-01` a `RN-DEL-10`, `RN-AUD-01` a `RN-AUD-07`, `RN-NTF-05`

## Contexto

Duas necessidades acopladas. A primeira: excluir um evento não pode apagá-lo — ele vai para uma lixeira,
fica restaurável por 72 horas e só então é elegível para remoção definitiva por um processo agendado que
precisa ser seguro e idempotente. A segunda: ações relevantes de negócio precisam de histórico.

Elas estão no mesmo ADR porque a purga definitiva **apaga a linha** e a auditoria **precisa sobreviver** a
isso. Decidir uma sem a outra produz um dos dois erros: auditoria que some junto com o dado, ou purga que
não pode rodar por causa de chave estrangeira.

## Decisão — Parte 1: soft delete

### 1. Colunas e filtro

```sql
deleted_at      timestamptz NULL,
deleted_by_user_id uuid     NULL REFERENCES app_user(id)
```

Na entidade JPA, `@SQLRestriction("deleted_at is null")` — o sucessor do `@Where`, depreciado no Hibernate 6.

**O risco que essa escolha cria, e como é tratado.** Um filtro global de entidade esconde o registro de
*todas* as consultas daquela entidade — inclusive das que precisam vê-lo: listar a lixeira, restaurar e
purgar. Se isso não for resolvido conscientemente, o desenvolvedor descobre do pior jeito: a restauração
"não encontra" o evento que ele acabou de excluir.

Solução: um repositório dedicado (`TrashedEventRepository`) com consultas nativas que **não** passam pela
restrição, usado exclusivamente pelas operações de lixeira. O acesso ao que está excluído fica confinado a
um tipo, não espalhado por flags booleanas em métodos de repositório normais.

**Alternativas consideradas.** Filtros explícitos em todo repositório (seguro, mas basta esquecer um método
para vazar evento excluído para o calendário — e essa falha é silenciosa); `@FilterDef` do Hibernate, que
precisa ser ativado por sessão e, se alguém esquecer de ativar, falha na direção insegura. O filtro global
falha na direção segura: o esquecimento esconde dados demais, não de menos.

### 2. Índice parcial

```sql
CREATE INDEX ix_event_period ON event (starts_at, ends_at) WHERE deleted_at IS NULL;
CREATE INDEX ix_event_trash  ON event (deleted_at) WHERE deleted_at IS NOT NULL;
```

Como praticamente toda consulta do calendário tem `deleted_at IS NULL`, o índice parcial fica menor e mais
denso que o índice completo. O segundo índice serve exclusivamente à lixeira e à purga, e permanece pequeno
por construção.

### 3. Escopo do soft delete

Apenas **eventos** no MVP. Grupos e usuários não recebem soft delete agora.

Motivo: soft delete se propaga. Um grupo "excluído" precisa desaparecer das listagens, dos cálculos de
permissão, da agregação do calendário e da pesquisa — cada um desses é um lugar onde esquecer o filtro vira
falha de segurança. Sem um caso de uso concreto de "desfazer exclusão de grupo", o custo não se paga.
Excluir um grupo continua sendo uma operação real e confirmada, e os eventos vinculados são desvinculados
(`ON DELETE RESTRICT` obriga a tratar isso explicitamente).

### 4. Cancelar ≠ excluir

São caminhos completamente separados (`RN-DEL-01`). Cancelar muda `status` para `CANCELLED` e o evento
continua visível. Excluir preenche `deleted_at`. Um evento cancelado pode ser excluído; um evento na lixeira
não pode ser cancelado.

Ambos são invisíveis para detecção de conflito e para disponibilidade (`RN-CFL-04`, `RN-AVL-04`) — por
motivos diferentes, e é bom que o código diga qual é qual.

### 5. Restauração

Exige a mesma permissão exigida para excluir (`RN-DEL-07`), verificada de novo no momento da restauração —
o usuário pode ter perdido o papel nesse meio-tempo. Restaurar limpa `deleted_at` e `deleted_by_user_id`.

Se um grupo vinculado deixou de existir enquanto o evento estava na lixeira, o vínculo já sumiu e o evento é
restaurado com os vínculos que ainda existirem — possivelmente nenhum, caso em que volta a ser evento
pessoal do criador (ADR-0005, item 4).

### 6. A purga agendada

```java
@Scheduled(cron = "0 15 * * * *")   // de hora em hora
```

Propriedades exigidas por `RN-DEL-06`:

- **Idempotente.** O critério é `deleted_at < now(clock) - 72h`. Rodar duas vezes seguidas não muda nada,
  porque a segunda execução não encontra mais as linhas da primeira.
- **Em lotes.** Processa no máximo N eventos por execução, com `LIMIT` e commit por lote. Uma purga que
  tenta apagar tudo em uma transação trava a tabela e falha inteira por causa de uma linha.
- **Respeita relacionamentos.** Participantes, comentários, notas, checklists e vínculos de grupo caem por
  `ON DELETE CASCADE`. O audit log **não** — ver a Parte 2.
- **Preserva auditoria.** A purga grava um registro `EVENT_PURGED` antes de apagar.
- **Desligável.** Uma propriedade de configuração permite desabilitar o agendamento no perfil de teste.

**ShedLock foi considerado e rejeitado por ora.** Ele resolve execução concorrente em múltiplas instâncias;
o projeto roda em instância única. Como a operação é idempotente, uma execução duplicada acidental não
corrompe nada — no pior caso repete trabalho já feito. Adotar ShedLock antes de existir a segunda instância
seria a abstração vazia que o projeto proíbe. Isto fica registrado como ponto de revisão explícito.

## Decisão — Parte 2: audit log

### 7. Tabela única polimórfica

```sql
CREATE TABLE audit_log (
    id           uuid        PRIMARY KEY,
    occurred_at  timestamptz NOT NULL,
    actor_id     uuid        NULL REFERENCES app_user(id),  -- NULL = ação do sistema
    action       varchar(64) NOT NULL,      -- EVENT_DELETED, GROUP_OWNERSHIP_TRANSFERRED, ...
    target_type  varchar(32) NOT NULL,      -- EVENT, GROUP, MEMBERSHIP, ...
    target_id    uuid        NOT NULL,
    group_id     uuid        NULL,          -- desnormalizado, para autorizar a leitura
    metadata     jsonb       NOT NULL DEFAULT '{}'
);
CREATE INDEX ix_audit_group_time  ON audit_log (group_id, occurred_at DESC);
CREATE INDEX ix_audit_target      ON audit_log (target_type, target_id, occurred_at DESC);
```

**Não há chave estrangeira para `target_id`**, e isso é deliberado: o registro precisa sobreviver à purga do
alvo. `group_id` é desnormalizado porque `VIEW_AUDIT_LOG` é uma permissão **de grupo** — sem essa coluna,
autorizar a leitura exigiria uma junção com uma linha que pode não existir mais.

**Alternativas consideradas.**

| Alternativa | Por que não |
|---|---|
| Uma tabela por domínio | Consultar "o que aconteceu neste grupo" viraria `UNION` entre N tabelas, e cada novo domínio mudaria a consulta. |
| Hibernate Envers | Audita **mudança de estado de entidade**, não **ação de negócio**. "Marcus confirmou presença" não é uma linha de diff de colunas. Envers ainda cria um schema-sombra e uma tabela de revisão que passariam a ser mantidas para sempre. |

### 8. Como o registro é escrito

Por **chamada explícita no service**, na mesma transação da operação de negócio.

**Alternativa rejeitada:** interceptor ou listener de entidade JPA. Ele veria "a coluna `role` mudou de
`COLLABORATOR` para `ADMIN`" e não teria como saber que aquilo foi uma promoção feita pelo `OWNER` a partir
de um endpoint específico. A intenção da ação não está no diff da linha — e é justamente a intenção que a
auditoria precisa registrar.

O custo é lembrar de chamar. É mitigado por testes: as operações sensíveis têm teste que verifica a
existência do registro correspondente.

### 9. Auditoria e notificação falham de formas diferentes

`RN-NTF-05` diz que falha de notificação não pode corromper a operação principal. Isso vale para
**notificação**, não para **auditoria**:

- **Auditoria** é escrita na mesma transação. Se ela falhar, a operação falha. Uma transferência de
  propriedade sem rastro é pior do que uma transferência que não aconteceu.
- **Notificação** é despachada **depois do commit**. Se o envio falhar, a operação permanece.

### 10. O que nunca entra no metadata

Senha, hash, token de sessão, código de convite completo, `client-secret`, cabeçalho `Authorization`
(`RN-AUD-04`). Códigos de convite entram apenas como prefixo ou hash, o suficiente para correlacionar.

Auditoria e log técnico são coisas diferentes e não se misturam: log técnico serve para depurar a
aplicação e pode ser descartado; auditoria é dado de negócio, é lido por usuários com permissão e é
paginado por uma API (`RN-AUD-05`, `RN-AUD-07`).

### 11. Imutabilidade

Não existe endpoint de atualização nem de exclusão de registro de auditoria (`RN-AUD-06`). A entidade JPA é
mapeada apenas para inserção e leitura.

## Escopo

**Fase 3 (MVP):** soft delete, lixeira, restauração e as consultas/índices correspondentes.
**Fase 5:** tabela `audit_log`, escrita nas operações sensíveis, leitura autorizada e paginada.
**Fase 5:** purga agendada.

Até a purga existir, eventos permanecem na lixeira indefinidamente — estado seguro e explicitamente
registrado em `docs/PROJECT_STATUS.md` enquanto durar.

## Testes obrigatórios

- Evento excluído não aparece em calendário, pesquisa, conflito nem disponibilidade.
- Evento excluído **aparece** na lixeira do usuário autorizado.
- Restauração funciona dentro de 72h e respeita permissão reavaliada no momento.
- Com `Clock` fixo em 71h59 nada é purgado; em 72h01 o evento é purgado.
- Purga executada duas vezes seguidas produz o mesmo resultado (idempotência).
- Purga em lote com mais registros que o tamanho do lote deixa o restante para a próxima execução.
- Após a purga, o registro de auditoria do evento continua legível.
- Registro de auditoria nunca contém senha, token ou código de convite completo.
- Falha ao gravar auditoria reverte a transferência de propriedade.

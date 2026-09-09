# PROJECT_SPEC — Shared Calendar

Especificação funcional e regras de negócio do projeto **Shared Calendar** (nome técnico provisório
`shared-calendar`).

Este documento é a **fonte de verdade sobre O QUE o sistema faz**. Decisões sobre **COMO** implementar
ficam em [`docs/DECISIONS.md`](docs/DECISIONS.md) e nos ADRs em [`docs/adr/`](docs/adr/). Prioridades e
ordem de construção ficam em [`docs/ROADMAP.md`](docs/ROADMAP.md). O estado real do que já foi construído
fica em [`docs/PROJECT_STATUS.md`](docs/PROJECT_STATUS.md).

Cada regra possui um identificador estável (`RN-XXX-nn`). Testes automatizados e ADRs devem referenciar
esses identificadores, para que exista rastreabilidade entre requisito, decisão e teste.

Marcadores de escopo usados neste documento:

| Marcador | Significado |
|---|---|
| `[MVP]` | Faz parte do produto mínimo utilizável. |
| `[PÓS-MVP]` | Planejado, mas não bloqueia o MVP. |
| `[FUTURO]` | Ideia aceita cuja arquitetura não pode ser inviabilizada, sem implementação prevista. |

---

## 1. Visão geral

**Problema.** Grupos de pessoas (família, turma da faculdade, colegas de academia, time de trabalho,
amigos de um projeto) coordenam compromissos por mensagens soltas. A informação se perde, ninguém sabe
quem confirmou presença, e encontrar um horário que sirva para todos vira uma negociação manual.

**Proposta.** Uma plataforma web onde cada pessoa tem seu calendário pessoal privado e participa de
vários grupos, cada um com seu próprio calendário, membros e permissões. Eventos podem ser compartilhados
entre grupos sem duplicação, participantes confirmam presença, e o sistema calcula janelas de horário
livre entre pessoas **sem revelar o conteúdo dos compromissos delas**.

**Objetivo profissional.** Ser uma peça de portfólio que demonstre autenticação, autorização granular,
modelagem relacional não trivial, tratamento correto de datas e fusos, processamento agendado idempotente,
privacidade aplicada no backend, testes de regra de negócio e infraestrutura reproduzível.

**Não-objetivos.** Não é um clone do Google Calendar. Não busca escala de milhões de usuários. Não usa
microserviços, mensageria distribuída ou IA como dependência central.

---

## 2. Glossário

| Termo | Definição |
|---|---|
| **Usuário** | Pessoa autenticada no sistema. |
| **Identificador público** | Código único e não sensível de um usuário, usado para que um administrador o adicione a um grupo sem expor e-mail. |
| **Calendário pessoal** | Calendário privado criado automaticamente para cada usuário. |
| **Grupo** | Conjunto de usuários com papéis, permissões e um calendário próprio. |
| **Calendário do grupo** | Conjunto de eventos associados a um grupo. |
| **Visão agregada** | Visualização que combina, em tela, eventos de vários calendários que o usuário selecionou. Não é uma cópia armazenada. |
| **Evento** | Compromisso com data/hora, podendo pertencer a zero, um ou vários grupos. |
| **Série** | Definição de um evento recorrente. |
| **Ocorrência** | Instância concreta de uma série em uma data específica. |
| **Exceção / override** | Ocorrência que foi modificada ou removida individualmente dentro de uma série. |
| **Participante** | Usuário convidado para um evento, com um estado de RSVP. |
| **RSVP** | Resposta do participante ao convite (`GOING`, `MAYBE`, `NOT_GOING`, `PENDING`). |
| **Organizador** | Usuário responsável pelo evento; por padrão o criador, podendo ser transferido. |
| **Disponibilidade (free/busy)** | Projeção de um calendário que informa apenas se há ou não compromisso em um intervalo, sem qualquer detalhe. |
| **Lixeira** | Estado de um evento com soft delete aplicado, ainda restaurável. |
| **Audit log** | Registro imutável de ações relevantes de negócio (diferente de log técnico da aplicação). |

---

## 3. Atores

| Ator | Descrição |
|---|---|
| **Visitante** | Não autenticado. Pode se cadastrar, autenticar e acessar um link de convite. |
| **Usuário** | Autenticado. Tem calendário pessoal e participa de grupos. |
| **Membro de grupo** | Usuário dentro de um grupo, com um papel (`OWNER`, `ADMIN`, `COLLABORATOR`, `VIEWER`). |
| **Organizador do evento** | Membro responsável por um evento específico. |
| **Processo agendado** | Rotina do sistema (envio de lembretes, purga da lixeira). Não é um usuário e não substitui autorização. |

---

## 4. Usuários, perfil e identidade

| ID | Regra | Escopo |
|---|---|---|
| `RN-USR-01` | Um usuário é identificado unicamente por e-mail. O e-mail é normalizado (trim + minúsculas) antes de comparar ou persistir. | MVP |
| `RN-USR-02` | Todo usuário recebe um **identificador público** gerado pelo sistema, único, sem relação derivável com e-mail ou nome, e seguro para exibir a terceiros. | MVP |
| `RN-USR-03` | Perfil contém: nome de exibição (obrigatório), e-mail (obrigatório), senha (obrigatória no cadastro tradicional), timezone (obrigatório, com padrão), identificador público (gerado), foto (opcional). | MVP |
| `RN-USR-04` | A foto de perfil é opcional e o sistema deve funcionar integralmente sem ela. | MVP |
| `RN-USR-05` | A busca de usuários por identificador público retorna apenas nome de exibição, identificador público e foto. **Nunca** retorna e-mail, grupos, eventos ou disponibilidade. | MVP |
| `RN-USR-06` | Não existe endpoint que liste todos os usuários da plataforma. Descoberta de pessoas acontece apenas dentro de um grupo do qual o solicitante participa, ou por identificador público exato. | MVP |
| `RN-USR-07` | Preferências do usuário: idioma, tema claro/escuro, formato 12h/24h, primeiro dia da semana, timezone, preferências de notificação e de lembrete padrão. | PÓS-MVP |
| `RN-USR-08` | O timezone do usuário é atributo do perfil e **nunca** é inferido do timezone do servidor. Ver `RN-TZ-01`. | MVP |

---

## 5. Calendários

| ID | Regra | Escopo |
|---|---|---|
| `RN-CAL-01` | Ao criar a conta, o usuário recebe automaticamente um calendário pessoal privado. | MVP |
| `RN-CAL-02` | O calendário pessoal é acessível apenas ao seu dono. Não existe compartilhamento de calendário pessoal no MVP. | MVP |
| `RN-CAL-03` | O calendário pessoal não pode ser excluído pelo usuário enquanto a conta existir. | MVP |
| `RN-CAL-04` | Cada grupo possui exatamente um calendário. | MVP |
| `RN-CAL-05` | O "calendário geral" do usuário é uma **visualização agregada** calculada sob demanda a partir dos calendários aos quais ele tem acesso. É proibido materializar cópias dos eventos por usuário. | MVP |
| `RN-CAL-06` | O usuário escolhe quais calendários aparecem na visão agregada. A seleção é preferência de exibição e não altera permissões. | MVP |
| `RN-CAL-07` | Cada calendário possui uma cor, escolhida de uma paleta acessível definida pelo sistema. A cor é atributo do calendário; uma preferência de cor por usuário fica para depois. | MVP |
| `RN-CAL-08` | Desmarcar um calendário na visão agregada não afeta notificações, lembretes nem convites. | MVP |

---

## 6. Grupos

### 6.1 Tipos e ciclo de vida

| ID | Regra | Escopo |
|---|---|---|
| `RN-GRP-01` | Um grupo tem tipo de visibilidade: `PRIVATE`, `INVITE_ONLY` ou `PUBLIC`. | MVP |
| `RN-GRP-02` | `PRIVATE`: não aparece em nenhuma busca. Entrada apenas por convite direto de um membro autorizado. | MVP |
| `RN-GRP-03` | `INVITE_ONLY`: não aparece em busca aberta, mas aceita entrada por link de convite ou código do grupo. | MVP |
| `RN-GRP-04` | `PUBLIC`: pode ser encontrado por busca. Ser encontrável **não** implica poder ver os eventos: um não-membro vê apenas nome, descrição e contagem de membros. | MVP |
| `RN-GRP-05` | Quem cria um grupo torna-se seu `OWNER`. | MVP |
| `RN-GRP-06` | Todo grupo tem exatamente um `OWNER` em qualquer momento. Essa invariante é garantida no banco, não apenas em código. | MVP |
| `RN-GRP-07` | Excluir um grupo é exclusivo do `OWNER` e exige confirmação explícita. | MVP |

### 6.2 Entrada em grupos

| ID | Regra | Escopo |
|---|---|---|
| `RN-GRP-10` | Existem três mecanismos de entrada: **link de convite**, **código do grupo** e **adição por administrador via identificador público**. | MVP |
| `RN-GRP-11` | Link de convite é um token com entropia criptográfica suficiente, com data de expiração, limite opcional de usos e possibilidade de revogação. Tokens são tratados como segredo. | MVP |
| `RN-GRP-12` | O código do grupo é distinto do link de convite: é curto, legível por humanos, regenerável e revogável. Deve ser gerado a partir de um alfabeto sem caracteres ambíguos. | MVP |
| `RN-GRP-13` | Tentar usar um token inválido, expirado, revogado ou esgotado retorna uma resposta genérica que não revela qual dessas condições ocorreu, nem se o grupo existe. | MVP |
| `RN-GRP-14` | Um administrador pode adicionar um usuário pelo identificador público exato. Não existe listagem ou autocomplete global de usuários (`RN-USR-06`). | MVP |
| `RN-GRP-15` | Um usuário adicionado a um grupo entra com o papel `VIEWER`, salvo se quem o adicionou definir explicitamente outro papel permitido. | MVP |
| `RN-GRP-16` | Entrar em um grupo é idempotente: repetir a operação para quem já é membro não duplica a associação nem altera o papel existente. | MVP |
| `RN-GRP-17` | Solicitação de entrada em grupo público com aprovação por administrador. | PÓS-MVP |

### 6.3 Saída e remoção

| ID | Regra | Escopo |
|---|---|---|
| `RN-GRP-20` | Um membro pode sair do grupo por conta própria, exceto o `OWNER`, que precisa antes transferir a propriedade ou excluir o grupo. | MVP |
| `RN-GRP-21` | Remover um membro não apaga os eventos que ele criou no grupo, nem seu histórico de auditoria. | MVP |
| `RN-GRP-22` | Ao sair ou ser removido, o usuário perde acesso ao calendário do grupo. Se continuar participante direto de um evento daquele grupo, mantém acesso apenas àquele evento. Ver `RN-MGR-08`. | MVP |

---

## 7. Papéis, permissões e autorização

### 7.1 Papéis

| ID | Regra | Escopo |
|---|---|---|
| `RN-AUTZ-01` | Os papéis são exatamente `OWNER`, `ADMIN`, `COLLABORATOR` e `VIEWER`. **Não existe papel `EDITOR`.** | MVP |
| `RN-AUTZ-02` | `OWNER`: todas as permissões do grupo, incluindo transferir propriedade e excluir o grupo. | MVP |
| `RN-AUTZ-03` | `ADMIN`: gestão operacional (membros, papéis abaixo do seu, eventos, notas, auditoria). **Não pode** excluir o grupo, transferir a propriedade, remover o `OWNER` nem alterar o papel do `OWNER`. | MVP |
| `RN-AUTZ-04` | `COLLABORATOR`: visualiza o calendário, cria eventos, edita e exclui **os próprios** eventos, participa de eventos e responde RSVP. | MVP |
| `RN-AUTZ-05` | `VIEWER`: visualiza o que lhe é permitido, responde RSVP e define seus próprios lembretes. Não cria nem modifica eventos. | MVP |
| `RN-AUTZ-06` | Um usuário não pode conceder a outro uma permissão que ele próprio não possui, nem promover alguém a um papel igual ou superior ao seu. | MVP |

### 7.2 Regra permanente de autoria

| ID | Regra | Escopo |
|---|---|---|
| `RN-AUTZ-10` | **Quem tem permissão para criar um evento tem, necessariamente, permissão para editar e excluir o evento que criou.** Essa regra é invariante e não pode ser removida por override. | MVP |
| `RN-AUTZ-11` | Por padrão, um usuário não altera eventos criados por outros. Isso exige permissão explícita (`EDIT_ANY_EVENT` / `DELETE_ANY_EVENT`) ou papel que a inclua. | MVP |

### 7.3 Catálogo de permissões

| ID | Regra | Escopo |
|---|---|---|
| `RN-AUTZ-20` | O catálogo inicial de permissões é: `VIEW_CALENDAR`, `CREATE_EVENT`, `EDIT_OWN_EVENT`, `EDIT_ANY_EVENT`, `DELETE_OWN_EVENT`, `DELETE_ANY_EVENT`, `MANAGE_MEMBERS`, `MANAGE_ROLES`, `MANAGE_GROUP`, `MANAGE_NOTES`, `VIEW_AUDIT_LOG`, `TRANSFER_OWNERSHIP`, `DELETE_GROUP`. | MVP |
| `RN-AUTZ-21` | Cada papel mapeia para um conjunto fixo e documentado de permissões. | MVP |
| `RN-AUTZ-22` | É possível registrar **overrides por membro**, concedendo ou revogando uma permissão específica sem trocar o papel. | PÓS-MVP |
| `RN-AUTZ-23` | Precedência de resolução: `DENY` explícito vence `GRANT` explícito, que vence o padrão do papel. Um `DENY` nunca pode violar `RN-AUTZ-10`. | PÓS-MVP |
| `RN-AUTZ-24` | Toda decisão de autorização é resolvida por um ponto central do domínio. É proibido espalhar condicionais de permissão por controllers, services ou frontend. | MVP |

### 7.4 Autorização no backend

| ID | Regra | Escopo |
|---|---|---|
| `RN-AUTZ-30` | Toda regra de autorização é verificada no backend. Ocultar um botão no frontend **não é** controle de acesso. | MVP |
| `RN-AUTZ-31` | Nenhum identificador enviado pelo cliente define ownership. O ator é sempre derivado da sessão autenticada. | MVP |
| `RN-AUTZ-32` | Acessar um recurso inexistente e acessar um recurso existente sem permissão devem produzir respostas indistinguíveis quando revelar a diferença permitiria enumerar recursos. | MVP |

### 7.5 Transferência de propriedade

| ID | Regra | Escopo |
|---|---|---|
| `RN-AUTZ-40` | Apenas o `OWNER` transfere a propriedade, e apenas para um membro ativo do grupo. | MVP |
| `RN-AUTZ-41` | A transferência é atômica: o novo dono vira `OWNER` e o antigo passa a `ADMIN` na mesma transação. Nunca existem dois donos nem nenhum dono. | MVP |
| `RN-AUTZ-42` | A transferência exige confirmação explícita e gera registro de auditoria (`RN-AUD-02`) e notificação a ambos. | MVP |

---

## 8. Eventos

### 8.1 Campos

| ID | Regra | Escopo |
|---|---|---|
| `RN-EVT-01` | Campos obrigatórios: título, instante/data de início, instante/data de fim (ou marcação de dia inteiro), criador, timezone de referência. | MVP |
| `RN-EVT-02` | Campos opcionais: descrição, local, organizador (padrão = criador), grupos, participantes, categoria, prioridade, cor, status, notas, checklist, responsáveis, lembretes, recorrência, anexos. | MVP / PÓS-MVP conforme a fase |
| `RN-EVT-03` | O fim de um evento nunca é anterior ao início. Eventos de duração zero são rejeitados. | MVP |
| `RN-EVT-04` | Todo evento registra data de criação e de última alteração, e quem realizou cada uma. | MVP |
| `RN-EVT-05` | Anexos de arquivo. | FUTURO |

### 8.2 Status

| ID | Regra | Escopo |
|---|---|---|
| `RN-EVT-10` | O MVP usa apenas `CONFIRMED` e `CANCELLED`. `PLANNED`, `IN_PROGRESS` e `COMPLETED` são derivados do relógio ou não agregam valor suficiente no MVP e ficam adiados; a decisão está registrada em `docs/DECISIONS.md`. | MVP |
| `RN-EVT-11` | "Em andamento" e "concluído" são estados **calculáveis** a partir do horário atual e não devem ser persistidos como status, sob risco de ficarem dessincronizados. | MVP |
| `RN-EVT-12` | Status persistido para fluxo de trabalho (ex.: tarefa concluída manualmente). | PÓS-MVP |

### 8.3 Dia inteiro e múltiplos dias

| ID | Regra | Escopo |
|---|---|---|
| `RN-EVT-20` | O sistema suporta evento de dia inteiro, evento com horário definido e evento que atravessa vários dias. | MVP |
| `RN-EVT-21` | Um evento de dia inteiro é definido por datas civis, não por instantes. Ele começa e termina no mesmo dia do calendário para qualquer observador, independentemente de fuso. Ver `RN-TZ-04`. | MVP |
| `RN-EVT-22` | Um evento com horário definido representa um instante absoluto no tempo e é exibido convertido para o fuso do observador. | MVP |
| `RN-EVT-23` | Converter um evento de dia inteiro em evento com horário (e vice-versa) é uma edição válida e deve preservar identidade, participantes e RSVP. | PÓS-MVP |

---

## 9. Eventos em múltiplos grupos

Regra estrutural do produto. Detalhamento da modelagem e das regras de autoridade fica no ADR
correspondente em `docs/adr/`.

| ID | Regra | Escopo |
|---|---|---|
| `RN-MGR-01` | Um evento pode estar associado a zero, um ou vários grupos. É **proibido** duplicar o evento por grupo. | MVP |
| `RN-MGR-02` | Um evento com zero grupos é um evento do calendário pessoal do criador. | MVP |
| `RN-MGR-03` | Para associar um evento a um grupo, o usuário precisa da permissão de criar/editar evento **naquele grupo específico**. | MVP |
| `RN-MGR-04` | Remover a associação com um grupo **não** exclui o evento e **não** o remove dos demais grupos. | MVP |
| `RN-MGR-05` | Remover a última associação de grupo não exclui o evento: ele volta a ser um evento pessoal do criador. | MVP |
| `RN-MGR-06` | Editar o conteúdo do evento (título, horário, descrição) exige permissão de edição sobre o **evento**, não sobre cada grupo. A regra de autoria (`RN-AUTZ-10`) continua valendo. | MVP |
| `RN-MGR-07` | Ao associar um evento a vários grupos, um usuário que pertence a mais de um deles é convidado **uma única vez**. Não existem participantes duplicados. | MVP |
| `RN-MGR-08` | Um usuário enxerga um evento se: for o criador; **ou** for participante direto; **ou** for membro de pelo menos um grupo ao qual o evento está associado e tiver `VIEW_CALENDAR` nesse grupo. | MVP |
| `RN-MGR-09` | Perder acesso a um grupo não remove o usuário da lista de participantes de eventos aos quais já foi convidado. | MVP |

---

## 10. Participantes e RSVP

| ID | Regra | Escopo |
|---|---|---|
| `RN-RSVP-01` | Estados de participação: `PENDING`, `GOING`, `MAYBE`, `NOT_GOING`. O estado inicial é sempre `PENDING`. | MVP |
| `RN-RSVP-02` | Responder ao próprio RSVP **não** exige permissão de edição do evento. Um `VIEWER` responde normalmente. | MVP |
| `RN-RSVP-03` | Um usuário só altera o próprio RSVP. Ninguém responde por outro. | MVP |
| `RN-RSVP-04` | Ao criar um evento com participantes, é possível convidar pessoas individualmente ou convidar todos os membros de um grupo associado. | MVP |
| `RN-RSVP-05` | Convidar "todos do grupo" resolve a lista no momento do convite. Quem entrar no grupo depois **não** é convidado automaticamente. | MVP |
| `RN-RSVP-06` | O organizador visualiza a contagem por estado (`GOING`, `MAYBE`, `NOT_GOING`, `PENDING`) e a lista de participantes com seus estados. | MVP |
| `RN-RSVP-07` | Criar um evento com participantes gera notificação de convite para cada convidado, exceto para o próprio criador. | MVP |
| `RN-RSVP-08` | O evento aparece no calendário do participante assim que ele é convidado, independentemente do RSVP ainda estar `PENDING`. | MVP |
| `RN-RSVP-09` | Remover um participante retira o evento da visão dele, salvo se ele mantiver acesso por outro caminho (`RN-MGR-08`). | MVP |
| `RN-RSVP-10` | Em eventos recorrentes, o RSVP pode ser respondido por ocorrência. Ver `RN-REC-10`. | PÓS-MVP |

---

## 11. Recorrência

Área de alto risco. A modelagem completa fica no ADR correspondente. Regras de produto:

| ID | Regra | Escopo |
|---|---|---|
| `RN-REC-01` | O sistema suporta recorrência diária, semanal (com dias específicos), mensal, com intervalo (ex.: a cada 2 semanas) e término por data ou por número de ocorrências. | PÓS-MVP (Fase 4) |
| `RN-REC-02` | É proibido inventar formato próprio de recorrência. O sistema adota um padrão consolidado e amplamente documentado. | PÓS-MVP |
| `RN-REC-03` | É proibido materializar ocorrências infinitas no banco. A expansão acontece sob demanda para um período consultado e limitado. | PÓS-MVP |
| `RN-REC-04` | Existem três operações distintas de edição: **somente esta ocorrência**, **esta e as próximas**, **toda a série**. | PÓS-MVP |
| `RN-REC-05` | As mesmas três semânticas valem para **excluir** e para **cancelar**. Cancelar uma ocorrência não a apaga. | PÓS-MVP |
| `RN-REC-06` | Uma ocorrência modificada individualmente deixa de acompanhar futuras alterações da série nos campos que ela sobrescreveu. | PÓS-MVP |
| `RN-REC-07` | A recorrência é ancorada no fuso de referência do evento. Mudança de horário de verão não pode deslocar o horário civil de uma série. Ver `RN-TZ-06`. | PÓS-MVP |
| `RN-REC-08` | Consultar um período retorna as ocorrências expandidas daquele período, com identificação estável de cada ocorrência. | PÓS-MVP |
| `RN-REC-09` | Recorrências com regras complexas do padrão adotado que não estejam na lista de `RN-REC-01` podem ser importadas e exibidas, mas não precisam ser editáveis pela interface no primeiro momento. | FUTURO |
| `RN-REC-10` | Participantes e RSVP em séries: a definição de participantes pertence à série; o RSVP pode ser sobrescrito por ocorrência. | PÓS-MVP |

---

## 12. Conflitos de horário

| ID | Regra | Escopo |
|---|---|---|
| `RN-CFL-01` | Ao criar ou mover um evento, o sistema detecta sobreposição com outros eventos visíveis ao usuário e **avisa**. | PÓS-MVP (Fase 4) |
| `RN-CFL-02` | Por padrão, conflito **não bloqueia** a operação. O usuário decide continuar. | PÓS-MVP |
| `RN-CFL-03` | A detecção considera: sobreposição parcial, sobreposição total, horários idênticos, eventos adjacentes (fim == início **não** é conflito), eventos de dia inteiro, eventos multi-dia e fusos diferentes. | PÓS-MVP |
| `RN-CFL-04` | Eventos cancelados e eventos na lixeira **não** geram conflito. | PÓS-MVP |
| `RN-CFL-05` | A mensagem de conflito só pode citar detalhes de eventos que o solicitante tem direito de ver. Para os demais, no máximo "há um compromisso neste horário". Ver `RN-AVL-03`. | PÓS-MVP |
| `RN-CFL-06` | A detecção de conflito é regra de domínio no backend e é executada também nas operações de arrastar e redimensionar. | PÓS-MVP |

---

## 13. Disponibilidade e privacidade

Funcionalidade de destaque do produto. Regra de privacidade **inegociável**.

| ID | Regra | Escopo |
|---|---|---|
| `RN-AVL-01` | O sistema calcula, para um conjunto de participantes e um período, os intervalos em que **todos** estão livres. | PÓS-MVP (Fase 7) |
| `RN-AVL-02` | O cálculo classifica o tempo apenas como `BUSY` ou `FREE`. | PÓS-MVP |
| `RN-AVL-03` | **Saber que alguém está ocupado não dá direito de ver o evento que causa a indisponibilidade.** A resposta nunca inclui título, descrição, local, participantes, grupo ou identificador do evento. | PÓS-MVP |
| `RN-AVL-04` | Eventos com RSVP `NOT_GOING` não contam como ocupado para aquele participante. Eventos cancelados e na lixeira também não contam. | PÓS-MVP |
| `RN-AVL-05` | Eventos de dia inteiro marcam o dia inteiro como ocupado no fuso do dono do calendário. | PÓS-MVP |
| `RN-AVL-06` | Só é possível consultar a disponibilidade de usuários com quem o solicitante compartilha ao menos um grupo. | PÓS-MVP |
| `RN-AVL-07` | A consulta é limitada em período máximo, número máximo de participantes e granularidade mínima de intervalo, e é sujeita a rate limiting, para não transformar a API em ferramenta de vigilância. | PÓS-MVP |
| `RN-AVL-08` | O resultado é apresentado no fuso escolhido pelo solicitante, com o fuso explicitado na resposta. | PÓS-MVP |
| `RN-AVL-09` | A funcionalidade não exige "duração mínima desejada" para funcionar, mas o cálculo deve ser modelado de forma que esse filtro possa ser adicionado depois sem reescrita. | PÓS-MVP |
| `RN-AVL-10` | Janela útil configurável (ex.: considerar apenas 08:00–22:00). | FUTURO |

---

## 14. Notas

| ID | Regra | Escopo |
|---|---|---|
| `RN-NOT-01` | Existem dois conceitos distintos: **notas de evento** e **notas de grupo**. | PÓS-MVP (Fase 5) |
| `RN-NOT-02` | Uma nota é `PRIVATE` (visível apenas ao autor) ou `SHARED` (visível a quem tem acesso ao evento/grupo). | PÓS-MVP |
| `RN-NOT-03` | Notas privadas são filtradas no backend em toda leitura, pesquisa, exportação e notificação. Nunca são enviadas ao cliente e escondidas na interface. | PÓS-MVP |
| `RN-NOT-04` | Editar ou excluir uma nota exige ser o autor ou possuir `MANAGE_NOTES` no grupo. Uma nota privada nunca é legível por `MANAGE_NOTES`. | PÓS-MVP |

---

## 15. Comentários

| ID | Regra | Escopo |
|---|---|---|
| `RN-CMT-01` | Eventos podem receber comentários de quem tem acesso ao evento. | PÓS-MVP (Fase 5) |
| `RN-CMT-02` | Um comentário gera notificação para os participantes conforme as preferências de cada um. O autor não é notificado do próprio comentário. | PÓS-MVP |
| `RN-CMT-03` | O autor edita e exclui o próprio comentário. Moderação de comentários alheios exige permissão administrativa. | PÓS-MVP |
| `RN-CMT-04` | Conteúdo de comentário é tratado como texto não confiável e nunca renderizado como HTML bruto. | PÓS-MVP |

---

## 16. Checklists e responsáveis

| ID | Regra | Escopo |
|---|---|---|
| `RN-CHK-01` | Checklist é um recurso **opcional** do evento. A interface só exibe a seção quando o usuário decide usá-la. | PÓS-MVP (Fase 5) |
| `RN-CHK-02` | Item de checklist tem descrição, estado (feito/não feito) e ordem. | PÓS-MVP |
| `RN-CHK-03` | Responsáveis por atividade são recurso **opcional** e não aparecem por padrão em todo evento. | PÓS-MVP |
| `RN-CHK-04` | O modelo deve permitir atribuir um responsável a um item de checklist no futuro, sem exigir isso agora. | PÓS-MVP |
| `RN-CHK-05` | Só é possível atribuir responsabilidade a quem já é participante do evento. | PÓS-MVP |

---

## 17. Lembretes

| ID | Regra | Escopo |
|---|---|---|
| `RN-LMB-01` | Existem dois tipos claramente distintos: **lembrete pessoal** e **lembrete obrigatório do evento**. | PÓS-MVP (Fase 6) |
| `RN-LMB-02` | Lembrete pessoal é configurado por cada usuário para si mesmo e só afeta ele. | PÓS-MVP |
| `RN-LMB-03` | Lembrete obrigatório é definido pelo organizador (ou usuário com permissão) e vale para todos os participantes do evento. | PÓS-MVP |
| `RN-LMB-04` | Um usuário pode adicionar lembretes pessoais além do obrigatório, mas não pode remover o obrigatório. | PÓS-MVP |
| `RN-LMB-05` | Opções predefinidas: 10 minutos, 30 minutos, 1 hora e 1 dia antes; além de valor personalizado. | PÓS-MVP |
| `RN-LMB-06` | O processamento de lembretes é **idempotente**: reinício da aplicação, execução concorrente ou retry não podem gerar envio duplicado. | PÓS-MVP |
| `RN-LMB-07` | Um lembrete cujo horário de disparo já passou por indisponibilidade do sistema é enviado com atraso ou descartado conforme uma janela de tolerância definida, nunca disparado em lote fora de contexto. | PÓS-MVP |
| `RN-LMB-08` | Cancelar ou excluir o evento cancela seus lembretes pendentes. | PÓS-MVP |

---

## 18. Notificações

| ID | Regra | Escopo |
|---|---|---|
| `RN-NTF-01` | Existe uma central de notificações dentro do sistema, com estado lido/não lido. | PÓS-MVP (Fase 6) |
| `RN-NTF-02` | Gatilhos previstos: convite para evento, alteração relevante de data/horário, cancelamento de evento, comentário, entrada/remoção de grupo, mudança de papel ou permissão, alteração de RSVP relevante ao organizador, lembrete e transferência de propriedade. | PÓS-MVP |
| `RN-NTF-03` | Alterações irrelevantes (ex.: correção de digitação na descrição) **não** geram notificação. A definição do que é "relevante" é explícita no código, não implícita. | PÓS-MVP |
| `RN-NTF-04` | O autor de uma ação não é notificado da própria ação. | PÓS-MVP |
| `RN-NTF-05` | Falha no canal de notificação **não** pode corromper nem reverter a operação de negócio principal. | PÓS-MVP |
| `RN-NTF-06` | Uma notificação nunca revela conteúdo que o destinatário não teria direito de ver pela API normal. | PÓS-MVP |
| `RN-NTF-07` | Web Push no navegador. | PÓS-MVP (Fase 6) |
| `RN-NTF-08` | Atualização em tempo real da interface. | PÓS-MVP (Fase 6) |
| `RN-NTF-09` | Notificações em aplicativo mobile. | FUTURO |

---

## 19. Cancelamento, exclusão e lixeira

| ID | Regra | Escopo |
|---|---|---|
| `RN-DEL-01` | **Cancelar** e **excluir** são operações diferentes e nunca sinônimas. | MVP |
| `RN-DEL-02` | Evento cancelado continua existindo e visível, com status `CANCELLED`, e permanece no histórico. | MVP |
| `RN-DEL-03` | Evento excluído recebe **soft delete**, sai das visualizações normais e vai para a lixeira. | MVP |
| `RN-DEL-04` | Um evento na lixeira permanece restaurável por **72 horas** a partir da exclusão. | MVP |
| `RN-DEL-05` | Após 72 horas, o evento fica elegível para remoção definitiva por um processo agendado. | PÓS-MVP |
| `RN-DEL-06` | O processo de purga é seguro, idempotente, executado em lotes, respeita relacionamentos e preserva os registros de auditoria da vida do evento. | PÓS-MVP |
| `RN-DEL-07` | Restaurar um evento exige a mesma permissão exigida para excluí-lo. | MVP |
| `RN-DEL-08` | A lixeira mostra apenas os eventos que o solicitante tem direito de ver e restaurar. | MVP |
| `RN-DEL-09` | Eventos na lixeira não aparecem em calendário, pesquisa, disponibilidade, conflitos ou notificações. | MVP |
| `RN-DEL-10` | Nenhuma chamada `DELETE` da API remove dados permanentemente de forma direta. | MVP |

---

## 20. Auditoria

| ID | Regra | Escopo |
|---|---|---|
| `RN-AUD-01` | O sistema registra ações **de negócio** relevantes em um audit log. Audit log e log técnico da aplicação são coisas diferentes e não se misturam. | PÓS-MVP (Fase 5) |
| `RN-AUD-02` | Ações auditadas incluem, no mínimo: criação/edição/cancelamento/exclusão/restauração de evento, alteração de data ou horário, alteração de participantes, RSVP, entrada e remoção de membro, mudança de papel ou permissão e transferência de propriedade. | PÓS-MVP |
| `RN-AUD-03` | Cada registro contém: quem, o quê, sobre qual entidade, quando e o contexto mínimo necessário. | PÓS-MVP |
| `RN-AUD-04` | **Nunca** registrar senha, hash de senha, token, chave, segredo OAuth ou credencial no audit log. | PÓS-MVP |
| `RN-AUD-05` | Ler o audit log de um grupo exige `VIEW_AUDIT_LOG`. | PÓS-MVP |
| `RN-AUD-06` | Registros de auditoria não são editáveis nem excluíveis pela aplicação. | PÓS-MVP |
| `RN-AUD-07` | A leitura do audit log é paginada e filtrável por período, ator e tipo de ação. | PÓS-MVP |

---

## 21. Pesquisa e filtros

| ID | Regra | Escopo |
|---|---|---|
| `RN-SRC-01` | A pesquisa global cobre eventos, grupos e notas compartilhadas aos quais o solicitante já tem acesso. | PÓS-MVP (Fase 4) |
| `RN-SRC-02` | A pesquisa aplica **exatamente** as mesmas regras de permissão da API normal. Não existe caminho privilegiado de leitura pela busca. | PÓS-MVP |
| `RN-SRC-03` | A pesquisa nunca retorna: eventos invisíveis ao solicitante, notas privadas de terceiros, grupos privados dos quais ele não participa, usuários fora dos seus grupos, ou detalhes de indisponibilidade. | PÓS-MVP |
| `RN-SRC-04` | Contagens de resultado e mensagens de erro não podem revelar a existência de recursos inacessíveis. | PÓS-MVP |
| `RN-SRC-05` | Filtros do calendário: calendário/grupo, participante, categoria, período, status, prioridade, "criados por mim" e "eventos em que participo". | PÓS-MVP |
| `RN-SRC-06` | Os filtros são aplicados no backend. O frontend não recebe dados que depois esconde. | PÓS-MVP |

---

## 22. Autenticação

| ID | Regra | Escopo |
|---|---|---|
| `RN-AUTH-01` | Cadastro e login com e-mail e senha. | MVP (Fase 1) |
| `RN-AUTH-02` | Senha nunca é armazenada em texto puro. É usada uma função de hash apropriada para senhas, com custo configurável. | MVP |
| `RN-AUTH-03` | Autenticação e autorização são conceitos distintos e implementados em camadas distintas. | MVP |
| `RN-AUTH-04` | Login com Google via OAuth2. | MVP (Fase 1, após o login tradicional) |
| `RN-AUTH-05` | Se o e-mail verificado do Google coincidir com uma conta existente, as identidades são vinculadas à mesma conta em vez de criar duplicata. | MVP |
| `RN-AUTH-06` | Mensagens de cadastro, login e recuperação de senha não revelam se um e-mail está cadastrado. | MVP |
| `RN-AUTH-07` | O endpoint de login tem rate limiting. | MVP |
| `RN-AUTH-08` | Logout invalida efetivamente a credencial de sessão no servidor, não apenas no cliente. | MVP |
| `RN-AUTH-09` | Tokens de convite e de recuperação têm expiração, entropia adequada, uso único quando aplicável e armazenamento seguro. | MVP / PÓS-MVP |
| `RN-AUTH-10` | Segredos OAuth e credenciais **nunca** entram no repositório. | MVP |
| `RN-AUTH-11` | Recuperação de senha por e-mail. | PÓS-MVP |
| `RN-AUTH-12` | Conta de demonstração para recrutadores, sem privilégios administrativos e com dados fictícios. | FUTURO |

---

## 23. Segurança e privacidade transversais

| ID | Regra | Escopo |
|---|---|---|
| `RN-SEC-01` | Toda entrada é validada no backend, independentemente da validação no frontend. | MVP |
| `RN-SEC-02` | Prevenção de IDOR: o acesso a qualquer recurso por identificador exige verificação de permissão do ator atual. | MVP |
| `RN-SEC-03` | CORS é configurado explicitamente, com origens conhecidas por ambiente. | MVP |
| `RN-SEC-04` | A proteção contra CSRF é decidida em função da estratégia de autenticação adotada e documentada no ADR correspondente. | MVP |
| `RN-SEC-05` | Respostas de erro nunca expõem stack trace, SQL, nome de tabela ou detalhe interno ao cliente. | MVP |
| `RN-SEC-06` | Acesso a dados é feito por consultas parametrizadas. Concatenação de SQL com entrada do usuário é proibida. | MVP |
| `RN-SEC-07` | Conteúdo gerado por usuário é tratado como não confiável na renderização. | MVP |
| `RN-SEC-08` | Segredos vivem apenas em variáveis de ambiente ou gerenciador de segredos. O repositório versiona apenas `.env.example` com placeholders. | MVP |
| `RN-SEC-09` | Segredos nunca são enviados ao frontend nem impressos em logs, testes ou CI. | MVP |
| `RN-SEC-10` | Upload de arquivos, quando existir, valida tipo, tamanho e destino, e nunca serve conteúdo do usuário a partir da origem da aplicação sem tratamento. | FUTURO |
| `RN-SEC-11` | Seeds, fixtures, testes e capturas de tela usam exclusivamente dados fictícios. | MVP |

---

## 24. Datas, horas e fuso horário

Requisito central do produto. A estratégia técnica detalhada fica no ADR correspondente.

| ID | Regra | Escopo |
|---|---|---|
| `RN-TZ-01` | O fuso do usuário é um atributo do perfil. O fuso do servidor nunca é usado como fuso do usuário. | MVP |
| `RN-TZ-02` | Instantes absolutos são armazenados em UTC. | MVP |
| `RN-TZ-03` | Eventos com horário definido guardam, além do instante, o fuso de referência em que foram criados, porque esse fuso é necessário para recorrência, exibição consistente e horário de verão. | MVP |
| `RN-TZ-04` | Eventos de dia inteiro são armazenados como datas civis, **sem** instante e sem fuso. Convertê-los para instante é considerado defeito. | MVP |
| `RN-TZ-05` | A API expressa instantes em formato padronizado e sem ambiguidade, e datas civis em formato de data pura. O contrato é documentado. | MVP |
| `RN-TZ-06` | Uma série recorrente mantém o **horário civil** no fuso de referência ao atravessar mudança de horário de verão. Uma reunião das 09:00 continua às 09:00. | PÓS-MVP |
| `RN-TZ-07` | Participantes em fusos diferentes veem o mesmo instante convertido para o próprio fuso. | MVP |
| `RN-TZ-08` | Toda regra que depende de "agora" usa um relógio injetável, para ser determinística em teste. | MVP |

---

## 25. Interface e experiência

| ID | Regra | Escopo |
|---|---|---|
| `RN-UX-01` | Menu principal: Home, Calendário, Grupos, Notas. Área global: pesquisa, botão `+ Criar`, notificações e perfil. | MVP |
| `RN-UX-02` | O botão `+ Criar` oferece: novo evento, novo grupo e nova anotação. | MVP |
| `RN-UX-03` | A Home é um dashboard com: compromissos de hoje, próximos eventos, convites pendentes com resposta rápida (Vou / Talvez / Não vou) e os grupos do usuário. | MVP |
| `RN-UX-04` | O calendário oferece as visualizações mês, semana, dia e agenda/lista. | MVP (Fase 3) |
| `RN-UX-05` | Arrastar para mudar data/horário e redimensionar para mudar duração. Antes de persistir, a operação valida permissão, regras do evento e conflitos no backend. | PÓS-MVP (Fase 4) |
| `RN-UX-06` | A aplicação é responsiva e utilizável em desktop, notebook, tablet e celular. | MVP |
| `RN-UX-07` | Toda tela que carrega dados trata explicitamente os estados de carregamento, vazio e erro. | MVP |
| `RN-UX-08` | Ações destrutivas exigem confirmação explícita e informam o que acontecerá. | MVP |
| `RN-UX-09` | A interface é navegável por teclado e respeita contraste adequado. | MVP |
| `RN-UX-10` | A identidade visual é original. É proibido copiar a identidade do Google Calendar, Outlook, Notion ou Trello. | MVP |
| `RN-UX-11` | PWA instalável. | FUTURO |

---

## 26. Integrações e IA

| ID | Regra | Escopo |
|---|---|---|
| `RN-INT-01` | Exportar um evento e um calendário em formato iCalendar (`.ics`). | FUTURO (Fase 8) |
| `RN-INT-02` | Importar arquivo `.ics`. Importação estática é diferente de sincronização contínua. | FUTURO |
| `RN-INT-03` | Formato próprio de calendário é proibido; o padrão iCalendar é seguido, incluindo tratamento de UID, fuso e recorrência. | FUTURO |
| `RN-INT-04` | Integração com Google Calendar, Outlook, Apple Calendar, Google Meet e Microsoft Teams. Nenhuma abstração ou tabela para essas integrações é criada antes do primeiro caso real. | FUTURO |
| `RN-IA-01` | Funcionalidades de IA (interpretar linguagem natural, sugerir horários, resumir a agenda) são opcionais. O produto funciona integralmente sem IA e a arquitetura central não depende dela. | FUTURO |

---

## 27. Casos de uso de referência

Estes cenários servem como critério de aceite ponta a ponta do produto.

### CU-1 — Grupo da Faculdade

1. Thiago cria o grupo `Grupo da Faculdade` e torna-se `OWNER` (`RN-GRP-05`).
2. Adiciona Marcus como `COLLABORATOR` e João como `VIEWER` (`RN-GRP-14`, `RN-GRP-15`).
3. Marcus cria a reunião "Reunião do TCC", sábado às 14h (`RN-AUTZ-04`).
4. Marcus consegue editar e excluir essa reunião porque a criou (`RN-AUTZ-10`).
5. João **não** consegue editar a reunião (`RN-AUTZ-05`, `RN-AUTZ-11`).
6. Marcus adiciona uma anotação ao evento (`RN-NOT-01`).
7. João é convidado, recebe notificação e confirma presença mesmo sendo `VIEWER` (`RN-RSVP-02`, `RN-RSVP-07`).
8. Uma hora antes, quem configurou lembrete pessoal recebe notificação (`RN-LMB-02`).
9. Se o organizador definiu lembrete obrigatório de 1 hora, **todos** os participantes recebem (`RN-LMB-03`).

### CU-2 — Academia

1. Thiago cria o grupo `Academia` e adiciona várias pessoas.
2. Cria a atividade "Treino", quarta-feira, e escolhe "convidar todos do grupo" (`RN-RSVP-04`).
3. Ao salvar: todos os convidados recebem notificação (`RN-RSVP-07`), o evento aparece no calendário de cada um com RSVP `PENDING` (`RN-RSVP-08`), e cada um responde Vou / Talvez / Não vou (`RN-RSVP-01`).
4. O organizador vê as contagens por estado (`RN-RSVP-06`).

### CU-3 — Evento em dois grupos

1. Thiago participa dos grupos `Faculdade` e `Startup`, com permissão de criar eventos em ambos.
2. Cria "Apresentação do Projeto" e associa aos dois grupos (`RN-MGR-01`, `RN-MGR-03`).
3. O evento aparece nos dois calendários, mas existe **um único** registro (`RN-MGR-01`).
4. Marcus, que está nos dois grupos, é convidado **uma única vez** (`RN-MGR-07`).
5. Thiago remove a associação com `Startup`; o evento continua existindo em `Faculdade` (`RN-MGR-04`).

### CU-4 — Privacidade na disponibilidade

1. João tem no calendário pessoal o evento "Consulta médica", das 15h às 16h.
2. Marcus, que compartilha um grupo com João, procura horário livre entre eles (`RN-AVL-06`).
3. Marcus vê que João está `BUSY` das 15h às 16h.
4. Marcus **não** consegue descobrir, por nenhum caminho da API, que o compromisso é uma consulta médica (`RN-AVL-03`).

### CU-5 — Cancelar vs. excluir

1. Um evento é **cancelado**: continua visível com status `CANCELLED` e some da detecção de conflito e da disponibilidade (`RN-DEL-02`, `RN-CFL-04`, `RN-AVL-04`).
2. Outro evento é **excluído**: some das visualizações e vai para a lixeira (`RN-DEL-03`).
3. Dentro de 72 horas, quem tinha permissão para excluí-lo consegue restaurá-lo (`RN-DEL-04`, `RN-DEL-07`).
4. Passadas 72 horas, ele fica elegível para purga definitiva pelo processo agendado (`RN-DEL-05`, `RN-DEL-06`).

---

## 28. Fora de escopo

Explicitamente **não** fazem parte deste produto:

- calendário de recursos físicos (salas, equipamentos) e reserva de recursos;
- videoconferência própria;
- chat em tempo real como funcionalidade principal;
- cobrança, assinatura ou pagamentos;
- multi-tenancy corporativo com administração de organização;
- aplicativo nativo publicado em loja;
- sincronização bidirecional contínua com calendários externos.

---

## 29. Rastreabilidade

| Documento | Papel |
|---|---|
| `PROJECT_SPEC.md` (este arquivo) | O que o sistema faz. Regras `RN-*`. |
| `AGENTS.md` | Como o trabalho é conduzido no repositório. |
| `docs/ROADMAP.md` | Em que ordem construir, com critérios de aceite. |
| `docs/DECISIONS.md` | Índice das decisões técnicas. |
| `docs/adr/` | Decisões arquiteturais detalhadas. |
| `docs/PROJECT_STATUS.md` | O que já existe de fato. |
| `docs/local-development.md` | Como executar o projeto. |

Toda regra de negócio relevante deve ter, quando implementada, ao menos um teste automatizado que
referencie seu identificador `RN-*`.

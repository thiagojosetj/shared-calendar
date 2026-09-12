# Desenvolvimento local

Como sair de uma máquina limpa até o projeto rodando. Este documento descreve apenas o que **já existe** —
seções são acrescentadas conforme o backend e o frontend forem construídos.

## Pré-requisitos

| Ferramenta | Versão mínima | Verificada em 2026-09-09 | Como conferir |
|---|---|---|---|
| Git | 2.40 | 2.53.0 | `git --version` |
| JDK | 21 | 21.0.10 | `java -version` |
| Node.js | 20 | 24.15.0 | `node -v` |
| Docker Desktop | 24 | 29.7.2 | `docker --version` |
| Docker Compose | v2 | v5.5.1 | `docker compose version` |

Maven **não** precisa estar instalado: o projeto usará o Maven Wrapper (`./mvnw`). PostgreSQL **não** deve
ser instalado na máquina — ele roda em container.

> **O Docker Desktop precisa estar aberto.** No Windows, o daemon só existe enquanto o aplicativo estiver em
> execução. Se `docker info` falhar com `failed to connect to the docker API`, o Docker Desktop está fechado.

## Primeira execução

```bash
git clone <url-do-repositorio>
cd shared-calendar
cp .env.example .env
```

Abra o `.env` e ajuste ao menos `POSTGRES_PASSWORD`. O `.env` é ignorado pelo Git e nunca deve ser
versionado.

```bash
docker compose up -d
```

Confira se o banco ficou saudável (a coluna `STATUS` deve mostrar `healthy`):

```bash
docker compose ps
```

## Portas e recursos

| Recurso | Nome / porta | Observação |
|---|---|---|
| Container do banco | `shared-calendar-postgres` | |
| Porta do banco | `127.0.0.1:5432` | Configurável por `POSTGRES_PORT` no `.env` |
| Volume de dados | `shared-calendar-postgres-data` | Sobrevive a `docker compose down` |
| Rede | `shared-calendar-network` | |
| Backend | `8080` | Ainda não existe |
| Frontend (dev) | `5173` | Ainda não existe |

Todos os nomes têm o prefixo `shared-calendar-` justamente para não colidir com outros projetos da máquina.

## Comandos do dia a dia

```bash
docker compose up -d          # sobe o banco
docker compose ps             # estado e healthcheck
docker compose logs -f postgres
docker compose stop           # para sem remover
docker compose down           # remove containers e rede, PRESERVA o volume
```

Conectar ao banco pelo `psql` de dentro do container (não é preciso ter `psql` na máquina):

```bash
docker compose exec postgres psql -U shared_calendar -d shared_calendar
```

### Comandos proibidos

Estes apagam dados ou afetam outros projetos da máquina. Não execute sem decisão consciente:

```text
docker compose down -v     # APAGA o volume e todos os dados locais
docker system prune        # afeta imagens/containers de TODOS os projetos
docker volume prune        # idem, para volumes
```

## Fuso horário

O container roda em **UTC** (`TZ` e `PGTZ`). Isso é deliberado: nenhuma regra de negócio pode depender do
fuso do servidor ([ADR-0002](adr/0002-datas-horas-e-fuso-horario.md)). Se algum código depender, precisa
quebrar aqui, em desenvolvimento — e não em produção, para um usuário em outro fuso.

## Troubleshooting

**`failed to connect to the docker API` / `dockerDesktopLinuxEngine`**
O Docker Desktop está fechado. Abra-o e espere o ícone da baleia ficar estável antes de repetir o comando.

**`Bind for 127.0.0.1:5432 failed: port is already allocated`**
Algo já usa a porta 5432 — normalmente um PostgreSQL instalado na máquina ou outro projeto em container.
Descubra o que é e, se preferir não mexer, altere `POSTGRES_PORT` no `.env` (por exemplo `5433`) e suba de
novo. Para descobrir no Windows:

```bash
netstat -ano | grep :5432
```

**`POSTGRES_PASSWORD: defina POSTGRES_PASSWORD no arquivo .env`**
O `.env` não existe ou está sem a variável. Rode `cp .env.example .env` e preencha.

**Container em loop: `docker compose ps` mostra `Restarting (1)`**

Sintoma: o container `shared-calendar-postgres` reinicia sem parar e nunca chega a `healthy`.
`docker compose logs postgres` mostra:

```text
Error: in 18+, these Docker images are configured to store database data in a format which is
compatible with pg_ctlcluster... there appears to be PostgreSQL data in: /var/lib/postgresql/data
(unused mount/volume)
```

Causa: a partir do PostgreSQL 18, a imagem oficial passou a usar `/var/lib/postgresql` como raiz dos dados
(o diretório real fica em `/var/lib/postgresql/18/docker`), em vez de `/var/lib/postgresql/data`. Um volume
montado no caminho antigo é tratado como dado de outra versão, e a imagem se recusa a iniciar para não
corromper nada. Mudança introduzida em
[docker-library/postgres#1259](https://github.com/docker-library/postgres/pull/1259).

Correção: o volume precisa ser montado na nova raiz. O `docker-compose.yml` do projeto já usa:

```yaml
volumes:
  - postgres-data:/var/lib/postgresql
```

Se o erro apareceu em um clone antigo, atualize o `docker-compose.yml` e recrie o volume **somente se ele
não tiver dados que você queira preservar** (o caso normal quando o banco nunca chegou a inicializar):

```bash
docker compose down
docker volume rm shared-calendar-postgres-data
docker compose up -d
docker compose ps        # aguarde (healthy)
```

Se o volume tiver dados reais de um PostgreSQL 17 ou anterior, apagar o volume perde esses dados, e só
trocar o caminho não resolve: é uma atualização de versão maior, que exige `pg_dump`/restauração ou
`pg_upgrade`. Não use `docker compose down -v` em nenhum dos casos.

Para confirmar onde os dados ficaram depois de subir:

```bash
docker compose exec postgres psql -U shared_calendar -d shared_calendar -c "show data_directory;"
```

**O healthcheck nunca fica `healthy`**
Veja os logs com `docker compose logs postgres`. A causa mais comum é o volume ter sido criado antes com
outro usuário/senha: o PostgreSQL só aplica `POSTGRES_USER` e `POSTGRES_PASSWORD` na **primeira**
inicialização do volume. Se o banco ainda não tem dados que você queira preservar, remova o volume
explicitamente e suba de novo:

```bash
docker compose down
docker volume rm shared-calendar-postgres-data
docker compose up -d
```

**Fim de linha estranho / `bash\r: command not found` em scripts**
O `.gitattributes` do projeto força LF nos arquivos que rodam em Linux. Se o problema aparecer em um clone
antigo, refaça o checkout dos arquivos:

```bash
git rm --cached -r . && git reset --hard
```

*(Esse comando descarta alterações locais não commitadas — confira `git status` antes.)*

## Ainda não disponível

Backend, frontend, migrations, seed de desenvolvimento e testes serão documentados aqui conforme forem
implementados. O estado atual está em [`PROJECT_STATUS.md`](PROJECT_STATUS.md).

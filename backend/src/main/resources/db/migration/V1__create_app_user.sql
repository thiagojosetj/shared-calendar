-- V1 — Tabela de usuários.
--
-- É a tabela raiz do modelo: praticamente todas as demais a referenciam.
-- As decisões que ela materializa estão em:
--   ADR-0001 (identificadores UUID v7 gerados na aplicação)
--   ADR-0002 (instantes em timestamptz, fuso do usuário como atributo do perfil)
--   ADR-0003 (hash de senha anulável, normalização em minúsculas, identificador público)

CREATE TABLE app_user (
    id             uuid         PRIMARY KEY,

    -- 320 é o tamanho máximo de um endereço de e-mail segundo a RFC 5321.
    email          varchar(320) NOT NULL,

    -- Anulável de propósito: contas criadas apenas pelo Google não têm senha.
    -- 255 (e não 100) porque um hash Argon2id com os parâmetros recomendados pela
    -- OWASP passa de 100 caracteres, e migrar depois custaria um ALTER TABLE em produção.
    password_hash  varchar(255) NULL,

    display_name   varchar(120) NOT NULL,

    -- Identificador público do usuário (RN-USR-02). Não é derivado do e-mail.
    public_handle  varchar(30)  NOT NULL,

    -- Fuso do usuário (RN-TZ-01). Nunca inferido do fuso do servidor.
    time_zone      varchar(64)  NOT NULL DEFAULT 'America/Sao_Paulo',

    avatar_url     text         NULL,

    status         varchar(16)  NOT NULL DEFAULT 'ACTIVE',
    email_verified boolean      NOT NULL DEFAULT false,

    created_at     timestamptz  NOT NULL,
    updated_at     timestamptz  NOT NULL,

    CONSTRAINT ck_app_user_status
        CHECK (status IN ('ACTIVE', 'SUSPENDED')),

    -- Formato do identificador público: minúsculas, dígitos e hífen, de 3 a 30 caracteres.
    -- Validado também na aplicação; aqui é a última linha de defesa.
    CONSTRAINT ck_app_user_public_handle_format
        CHECK (public_handle ~ '^[a-z0-9][a-z0-9-]{1,28}[a-z0-9]$')
);

-- Unicidade insensível a maiúsculas. A aplicação também normaliza na escrita (ADR-0003):
-- o índice sozinho impediria o duplicado, mas não faria `findByEmail` encontrar o registro
-- de quem digitou o e-mail com outra caixa.
CREATE UNIQUE INDEX ux_app_user_email  ON app_user (lower(email));
CREATE UNIQUE INDEX ux_app_user_handle ON app_user (lower(public_handle));

COMMENT ON TABLE  app_user               IS 'Usuários da plataforma.';
COMMENT ON COLUMN app_user.public_handle IS 'Identificador público, seguro para exibir a terceiros (RN-USR-02).';
COMMENT ON COLUMN app_user.password_hash IS 'Nulo para contas criadas apenas por login social.';
COMMENT ON COLUMN app_user.time_zone     IS 'Fuso IANA do usuário. Nunca o fuso do servidor (RN-TZ-01).';

create table outbox_event (
    id             uuid        primary key,
    seq            bigint generated always as identity,
    aggregate_type varchar(32) not null,
    aggregate_id   uuid        not null,
    event_type     varchar(64) not null,
    payload        jsonb       not null,
    created_at     timestamptz not null,
    published_at   timestamptz
);

create unique index outbox_event_seq_uq on outbox_event (seq);
create index outbox_event_unpublished_idx on outbox_event (seq) where published_at is null;

create table processed_event (
    consumer     varchar(64) not null,
    event_id     uuid        not null,
    processed_at timestamptz not null,
    primary key (consumer, event_id)
);

create table statement_entry (
    entry_id                bigint      primary key,
    account_id              uuid        not null,
    transfer_id             uuid        not null,
    transfer_type           varchar(16) not null,
    counterparty_account_id uuid        not null,
    amount                  bigint      not null,
    currency                char(3)     not null,
    balance_after           bigint      not null,
    posted_at               timestamptz not null
);

create index statement_entry_account_id_entry_id_idx on statement_entry (account_id, entry_id);

create table account_balance_view (
    account_id    uuid        primary key,
    currency      char(3)     not null,
    balance       bigint      not null,
    last_entry_id bigint      not null,
    entry_count   bigint      not null,
    updated_at    timestamptz not null
);

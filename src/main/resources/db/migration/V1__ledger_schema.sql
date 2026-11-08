create table account (
    id         uuid         primary key,
    owner      varchar(200) not null,
    currency   char(3)      not null,
    type       varchar(16)  not null,
    status     varchar(16)  not null,
    balance    bigint       not null default 0,
    version    bigint       not null default 0,
    created_at timestamptz  not null,
    constraint account_type_check check (type in ('CUSTOMER', 'SETTLEMENT')),
    constraint account_status_check check (status in ('ACTIVE', 'CLOSED')),
    constraint account_balance_check check (type = 'SETTLEMENT' or balance >= 0)
);

create unique index account_settlement_currency_uq on account (currency) where type = 'SETTLEMENT';

create table transfer (
    id              uuid         primary key,
    idempotency_key varchar(255) not null,
    type            varchar(16)  not null,
    from_account_id uuid         not null references account (id),
    to_account_id   uuid         not null references account (id),
    amount          bigint       not null,
    currency        char(3)      not null,
    status          varchar(16)  not null,
    reason          varchar(64),
    created_at      timestamptz  not null,
    constraint transfer_idempotency_key_uq unique (idempotency_key),
    constraint transfer_type_check check (type in ('TRANSFER', 'DEPOSIT')),
    constraint transfer_status_check check (status in ('COMPLETED', 'REJECTED')),
    constraint transfer_amount_check check (amount > 0),
    constraint transfer_distinct_accounts_check check (from_account_id <> to_account_id),
    constraint transfer_reason_check check (
        (status = 'COMPLETED' and reason is null) or (status = 'REJECTED' and reason is not null)
    )
);

create index transfer_from_account_id_idx on transfer (from_account_id);
create index transfer_to_account_id_idx on transfer (to_account_id);

create table ledger_entry (
    id          bigint generated always as identity primary key,
    transfer_id uuid        not null references transfer (id),
    account_id  uuid        not null references account (id),
    amount      bigint      not null,
    currency    char(3)     not null,
    created_at  timestamptz not null,
    constraint ledger_entry_amount_check check (amount <> 0)
);

create index ledger_entry_account_id_id_idx on ledger_entry (account_id, id);
create index ledger_entry_transfer_id_idx on ledger_entry (transfer_id);

create table idempotency_record (
    idempotency_key varchar(255) primary key,
    request_hash    char(64)     not null,
    transfer_id     uuid         references transfer (id),
    created_at      timestamptz  not null
);

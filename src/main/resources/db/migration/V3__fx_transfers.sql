alter table account drop constraint account_type_check;
alter table account add constraint account_type_check check (type in ('CUSTOMER', 'SETTLEMENT', 'FX_POSITION'));

alter table account drop constraint account_balance_check;
alter table account add constraint account_balance_check check (type <> 'CUSTOMER' or balance >= 0);

create unique index account_fx_position_currency_uq on account (currency) where type = 'FX_POSITION';

alter table transfer add column target_amount bigint;
alter table transfer add column target_currency char(3);
alter table transfer add column fx_rate numeric(24, 12);
alter table transfer add column fx_rate_source varchar(64);
alter table transfer add column fx_quoted_at timestamptz;

alter table transfer add constraint transfer_fx_check check (
    (target_amount is null and target_currency is null and fx_rate is null
        and fx_rate_source is null and fx_quoted_at is null)
    or (target_amount > 0 and target_currency is not null and target_currency <> currency
        and fx_rate > 0 and fx_rate_source is not null and fx_quoted_at is not null)
);

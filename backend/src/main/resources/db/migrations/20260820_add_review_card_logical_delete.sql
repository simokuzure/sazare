begin;

alter table review_cards
    add column if not exists deleted boolean not null default false;

comment on column review_cards.deleted is '逻辑删除标记：false=未删除，true=已删除';

alter table user_error_types
    drop constraint if exists uq_user_error_types_user_type_name;
drop index if exists uq_user_error_types_user_type_name;
create unique index if not exists uq_user_error_types_user_type_name
    on user_error_types (user_id, error_type_id, name)
    where status = 'ACTIVE';

drop index if exists idx_review_cards_user_status_due_at;
create index if not exists idx_review_cards_user_status_due_at
    on review_cards (user_id, deleted, status, due_at);

commit;

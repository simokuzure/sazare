create table if not exists tatoeba_candidates (
    id bigserial primary key,
    zh_sentence_id bigint not null,
    original_source_text text not null,
    raw_answers jsonb not null,
    completed_question jsonb,
    status varchar(32) not null default 'UNPROCESSED',
    question_id bigint references questions(id),
    last_error text,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    constraint uk_tatoeba_candidates_zh_sentence_id unique (zh_sentence_id),
    constraint uk_tatoeba_candidates_question_id unique (question_id),
    constraint ck_tatoeba_candidates_zh_sentence_id check (zh_sentence_id > 0),
    constraint ck_tatoeba_candidates_raw_answers check (
        jsonb_typeof(raw_answers) = 'array' and jsonb_array_length(raw_answers) > 0
    ),
    constraint ck_tatoeba_candidates_completed_question check (
        completed_question is null or jsonb_typeof(completed_question) = 'object'
    ),
    constraint ck_tatoeba_candidates_status check (
        status in ('UNPROCESSED', 'COMPLETED', 'COMPLETION_FAILED', 'IMPORTED')
    )
);

comment on table tatoeba_candidates is 'Tatoeba中日语料候选表';
comment on column tatoeba_candidates.id is '候选记录主键ID';
comment on column tatoeba_candidates.zh_sentence_id is 'Tatoeba中文句子ID';
comment on column tatoeba_candidates.original_source_text is 'Tatoeba原始中文句子';
comment on column tatoeba_candidates.raw_answers is '同一中文句子对应的原始日文译文数组';
comment on column tatoeba_candidates.completed_question is 'AI补全后的题目JSON，结构与AI生题的单题JSON一致';
comment on column tatoeba_candidates.status is '处理状态：UNPROCESSED=未处理，COMPLETED=已补全，COMPLETION_FAILED=补全校验失败，IMPORTED=已入库';
comment on column tatoeba_candidates.question_id is '正式入库后的题目ID';
comment on column tatoeba_candidates.last_error is '最近一次处理错误';

create index if not exists idx_tatoeba_candidates_status_id
    on tatoeba_candidates (status, id);

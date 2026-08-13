begin;

alter table user_answers
    add column if not exists ai_revised_text text null;

alter table user_answers
    alter column question_id drop not null;

alter table user_answers
    drop constraint if exists ck_user_answers_question_or_correction;

alter table user_answers
    add constraint ck_user_answers_question_or_correction check (
        question_id is not null or ai_revised_text is not null
    );

comment on column user_answers.question_id is
    '题目ID，对应 questions.id，由代码维护有效性；纯日语纠错记录为空';
comment on column user_answers.ai_revised_text is
    'AI修订后的完整日语文本，纯日语纠错记录使用';

alter table user_answer_errors
    alter column question_id drop not null;

comment on column user_answer_errors.question_id is
    '题目ID，冗余保存自 user_answers.question_id；纯日语纠错记录为空';

commit;

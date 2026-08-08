-- 既有数据库增量迁移：先切换到 pgvector PostgreSQL 16 镜像，再执行本文件。
create extension if not exists vector;

create table if not exists question_embeddings (
    question_id bigint primary key references questions(id),
    embedding vector(768) not null,
    content_hash char(64) not null,
    model_name varchar(128) not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

comment on table question_embeddings is '题目语义向量表，仅用于常规题生成去重';
comment on column question_embeddings.question_id is '题目ID';
comment on column question_embeddings.embedding is '768维语义向量';
comment on column question_embeddings.content_hash is '原文和语境规范化内容哈希';
comment on column question_embeddings.model_name is '嵌入模型名称';

create index if not exists idx_question_embeddings_embedding_hnsw
    on question_embeddings using hnsw (embedding vector_cosine_ops);

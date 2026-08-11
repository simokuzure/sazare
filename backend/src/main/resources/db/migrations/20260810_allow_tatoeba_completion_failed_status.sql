alter table tatoeba_candidates
    drop constraint if exists ck_tatoeba_candidates_status;

alter table tatoeba_candidates
    add constraint ck_tatoeba_candidates_status check (
        status in ('UNPROCESSED', 'COMPLETED', 'COMPLETION_FAILED', 'IMPORTED')
    );

comment on column tatoeba_candidates.status is
    '处理状态：UNPROCESSED=未处理，COMPLETED=已补全，COMPLETION_FAILED=补全校验失败，IMPORTED=已入库';

-- V2 idempotent legacy upgrades and data backfills.
-- These run after V1 on every database. On a fresh install (V1 already created the full
-- column set) they are no-ops; on an older non-empty database (baseline-on-migrate) they add the
-- columns and indexes introduced after the first release, before the indexes that depend on them.

-- Columns added after the initial release.
alter table review_task add column if not exists pull_request_id bigint;
alter table review_task add column if not exists base_commit_id_normalized varchar(80) not null default '';
alter table user_account add column if not exists session_version integer not null default 0;
alter table ai_call_log add column if not exists prompt_tokens integer not null default 0;
alter table ai_call_log add column if not exists completion_tokens integer not null default 0;
alter table ai_call_log add column if not exists total_tokens integer not null default 0;
alter table feedback add column if not exists updated_at timestamp(6) with time zone;

-- Backfills for the new columns.
update review_task set base_commit_id_normalized = coalesce(base_commit_id, '')
    where base_commit_id_normalized is null or base_commit_id_normalized = '';
update feedback set updated_at = created_at where updated_at is null;
alter table feedback alter column updated_at set not null;

-- review_task indexes that reference post-release columns.
create index if not exists idx_review_task_pull_request on review_task(pull_request_id);
create unique index if not exists uq_review_task_idempotency
    on review_task(project_id, repository_id, commit_id, base_commit_id_normalized, branch_name);

-- One verdict per user per issue: drop accidental duplicates before enforcing uniqueness.
delete from feedback f
using feedback dup
where f.issue_id = dup.issue_id
  and f.user_id = dup.user_id
  and f.id < dup.id;
create index if not exists idx_feedback_issue on feedback(issue_id);
create unique index if not exists uq_feedback_issue_user on feedback(issue_id, user_id);

-- knowledge_chunk_vector foreign key: clean orphans, then (re)assert the constraint.
delete from knowledge_chunk_vector kv
where not exists (
    select 1 from knowledge_chunk kc where kc.id = kv.chunk_id
);
alter table knowledge_chunk_vector
    drop constraint if exists fk_knowledge_chunk_vector_chunk;
alter table knowledge_chunk_vector
    add constraint fk_knowledge_chunk_vector_chunk
    foreign key (chunk_id) references knowledge_chunk(id) on delete cascade;

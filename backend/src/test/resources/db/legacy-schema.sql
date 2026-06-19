-- Representative first-release schema, predating the V2 migration.
-- Intentionally MISSING: review_task.pull_request_id, review_task.base_commit_id_normalized,
-- user_account.session_version, ai_call_log token columns, feedback.updated_at, and every
-- index/constraint that V2 introduces. The legacy-upgrade test loads this, then runs Flyway
-- with baseline-on-migrate to prove the upgrade path is non-destructive and idempotent.
create extension if not exists vector;

create table user_account (
    id bigserial primary key,
    username varchar(64) not null unique,
    password_hash varchar(255) not null,
    nickname varchar(64),
    role varchar(32) not null,
    status varchar(32) not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

create table project (
    id bigserial primary key,
    owner_id bigint not null,
    name varchar(128) not null,
    description text,
    default_branch varchar(128),
    status varchar(32) not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

create table code_repository (
    id bigserial primary key,
    project_id bigint not null,
    repo_url varchar(512) not null,
    provider varchar(32) not null,
    default_branch varchar(128) not null,
    access_token text,
    local_path varchar(512),
    status varchar(32) not null,
    last_error text,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

create table pull_request (
    id bigserial primary key,
    project_id bigint not null,
    repository_id bigint not null,
    provider varchar(32) not null,
    external_pr_id varchar(128),
    pr_number integer,
    title varchar(255) not null,
    author_name varchar(128),
    source_branch varchar(128) not null,
    target_branch varchar(128) not null,
    base_sha varchar(80) not null,
    head_sha varchar(80) not null,
    status varchar(32) not null,
    review_state varchar(32) not null,
    last_synced_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

create table knowledge_document (
    id bigserial primary key,
    project_id bigint not null,
    uploader_id bigint not null,
    doc_type varchar(64) not null,
    file_name varchar(255) not null,
    content_text text,
    status varchar(32) not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

create table knowledge_chunk (
    id bigserial primary key,
    document_id bigint not null,
    project_id bigint not null,
    doc_type varchar(64) not null,
    source_name varchar(255) not null,
    chunk_index integer not null,
    content text not null,
    embedding_json text,
    created_at timestamp(6) with time zone not null
);

-- review_task WITHOUT pull_request_id and base_commit_id_normalized.
create table review_task (
    id bigserial primary key,
    project_id bigint not null,
    repository_id bigint not null,
    commit_id varchar(80) not null,
    base_commit_id varchar(80),
    branch_name varchar(128) not null,
    trigger_user_id bigint not null,
    status varchar(32) not null,
    retry_count integer not null,
    diff_text text,
    knowledge_doc_ids text,
    error_message text,
    started_at timestamp(6) with time zone,
    finished_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null
);

create table review_report (
    id bigserial primary key,
    task_id bigint not null,
    project_id bigint not null,
    commit_id varchar(80) not null,
    overall_risk varchar(32) not null,
    issue_count integer not null,
    summary text,
    raw_ai_response text,
    created_at timestamp(6) with time zone not null
);

create table review_issue (
    id bigserial primary key,
    report_id bigint not null,
    severity varchar(32) not null,
    category varchar(64) not null,
    file_path varchar(512),
    line_start integer,
    line_end integer,
    title varchar(255) not null,
    description text,
    impact text,
    evidence text,
    suggestion text,
    confidence double precision,
    created_at timestamp(6) with time zone not null
);

create table review_action (
    id bigserial primary key,
    project_id bigint not null,
    pull_request_id bigint not null,
    report_id bigint,
    actor_id bigint not null,
    action_type varchar(32) not null,
    reason text,
    requirement_text text,
    selected_issue_ids text,
    created_at timestamp(6) with time zone not null
);

-- feedback WITHOUT updated_at, and with a duplicate (issue_id, user_id) pair to exercise the
-- V2 de-duplication before the unique index is created.
create table feedback (
    id bigserial primary key,
    issue_id bigint not null,
    user_id bigint not null,
    feedback_type varchar(32) not null,
    comment text,
    created_at timestamp(6) with time zone not null
);
insert into feedback (issue_id, user_id, feedback_type, comment, created_at)
values (1, 1, 'AGREE', 'first', now()),
       (1, 1, 'AGREE', 'duplicate to be removed', now());

create table mq_task_log (
    id bigserial primary key,
    task_id bigint,
    message_id varchar(128) not null,
    exchange_name varchar(128) not null,
    routing_key varchar(128) not null,
    queue_name varchar(128) not null,
    payload text,
    status varchar(32) not null,
    retry_count integer not null,
    error_message text,
    created_at timestamp(6) with time zone not null
);

-- ai_call_log WITHOUT the token columns.
create table ai_call_log (
    id bigserial primary key,
    project_id bigint,
    task_id bigint,
    request_type varchar(64) not null,
    provider varchar(64) not null,
    model varchar(128) not null,
    prompt_chars integer not null,
    response_chars integer not null,
    latency_ms bigint not null,
    status varchar(32) not null,
    error_message text,
    created_at timestamp(6) with time zone not null
);

create table knowledge_chunk_vector (
    chunk_id bigint primary key,
    project_id bigint not null,
    embedding vector not null
);

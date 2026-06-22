-- V3 Agent control plane: persistent runs, steps, and state machine.

create table if not exists agent_run (
    id bigserial primary key,
    trigger_key varchar(255) not null unique,
    project_id bigint not null,
    repository_id bigint not null,
    pull_request_id bigint,
    head_sha varchar(80) not null,
    base_sha varchar(80),
    status varchar(32) not null,
    current_step_sequence integer not null default 0,
    cancellation_requested boolean not null default false,
    failure_type varchar(64),
    failure_message text,
    started_at timestamp(6) with time zone not null,
    completed_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    version integer not null default 0
);

create index if not exists idx_agent_run_status_updated on agent_run(status, updated_at);
create index if not exists idx_agent_run_project on agent_run(project_id);
create index if not exists idx_agent_run_repository on agent_run(repository_id);
create index if not exists idx_agent_run_pull_request on agent_run(pull_request_id);

create table if not exists agent_step (
    id bigserial primary key,
    agent_run_id bigint not null,
    sequence_no integer not null,
    step_type varchar(64) not null,
    status varchar(32) not null,
    attempt integer not null default 1,
    input_summary text,
    output_summary text,
    error_message text,
    started_at timestamp(6) with time zone,
    completed_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    constraint uq_agent_step_run_sequence unique (agent_run_id, sequence_no)
);

create index if not exists idx_agent_step_run on agent_step(agent_run_id, sequence_no);
create index if not exists idx_agent_step_status on agent_step(status, updated_at);

alter table agent_step
    add constraint fk_agent_step_run
    foreign key (agent_run_id) references agent_run(id) on delete cascade;

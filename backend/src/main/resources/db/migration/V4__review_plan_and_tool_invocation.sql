-- V4 review plan and tool invocation tracking

create table if not exists review_plan (
    id bigserial primary key,
    agent_run_id bigint not null unique,
    model_response text,
    plan_json text not null,
    validation_errors text,
    created_at timestamp(6) with time zone not null,
    constraint fk_review_plan_run foreign key (agent_run_id) references agent_run(id) on delete cascade
);

create index if not exists idx_review_plan_run on review_plan(agent_run_id);

create table if not exists tool_invocation (
    id bigserial primary key,
    agent_run_id bigint not null,
    agent_step_id bigint not null,
    tool_name varchar(128) not null,
    invocation_key varchar(255) not null unique,
    input_json text,
    output_json text,
    status varchar(32) not null,
    duration_ms bigint,
    error_message text,
    created_at timestamp(6) with time zone not null,
    constraint fk_tool_invocation_run foreign key (agent_run_id) references agent_run(id) on delete cascade,
    constraint fk_tool_invocation_step foreign key (agent_step_id) references agent_step(id) on delete cascade
);

create index if not exists idx_tool_invocation_run on tool_invocation(agent_run_id);
create index if not exists idx_tool_invocation_step on tool_invocation(agent_step_id);

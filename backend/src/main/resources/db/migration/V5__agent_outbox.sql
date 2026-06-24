-- V5 Agent outbox for transactional message publishing

create table if not exists agent_outbox_event (
    id bigserial primary key,
    agent_run_id bigint not null,
    event_type varchar(64) not null,
    payload text not null,
    status varchar(32) not null,
    attempt integer not null default 0,
    next_attempt_at timestamp(6) with time zone,
    sent_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone not null,
    constraint fk_outbox_run foreign key (agent_run_id) references agent_run(id) on delete cascade
);

create index if not exists idx_outbox_status_next on agent_outbox_event(status, next_attempt_at);
create index if not exists idx_outbox_run on agent_outbox_event(agent_run_id);

package com.example.codereview.agent.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AgentOutboxRepository extends JpaRepository<AgentOutboxEvent, Long> {
}

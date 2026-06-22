package com.example.codereview.agent.run;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AgentRunRepository extends JpaRepository<AgentRun, Long> {

    Optional<AgentRun> findByTriggerKey(String triggerKey);
}

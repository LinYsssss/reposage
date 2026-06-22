package com.example.codereview.agent.run;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentStepRepository extends JpaRepository<AgentStep, Long> {

    List<AgentStep> findByAgentRunIdOrderBySequenceNoAsc(Long agentRunId);
}

package com.example.codereview.agent.tool;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of typed Agent tools.
 * Constructed at startup from all AgentTool beans.
 */
@Component
public class AgentToolRegistry {

    private final Map<String, AgentTool<?, ?>> tools;

    public AgentToolRegistry(List<AgentTool<?, ?>> toolList) {
        this.tools = new HashMap<>();
        for (AgentTool<?, ?> tool : toolList) {
            if (tools.containsKey(tool.name())) {
                throw new IllegalStateException("Duplicate tool name: " + tool.name());
            }
            tools.put(tool.name(), tool);
        }
    }

    /**
     * Looks up a tool by name.
     */
    public Optional<AgentTool<?, ?>> getTool(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    /**
     * Returns all registered tool names.
     */
    public Iterable<String> getAllToolNames() {
        return tools.keySet();
    }

    /**
     * Returns the number of registered tools.
     */
    public int size() {
        return tools.size();
    }
}

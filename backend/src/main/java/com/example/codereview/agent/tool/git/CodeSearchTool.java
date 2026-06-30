package com.example.codereview.agent.tool.git;

import com.example.codereview.agent.tool.AgentTool;
import com.example.codereview.agent.tool.ToolContext;
import com.example.codereview.agent.tool.ToolResult;
import com.example.codereview.agent.tool.ToolRiskLevel;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * {@code code.search} — searches the prepared repository for a pattern in the sandbox, returning a
 * bounded list of {@code file:line} matches.
 */
@Component
public class CodeSearchTool implements AgentTool<CodeSearchTool.Input, String> {

    public record Input(String pattern) {
    }

    private final SandboxToolGateway gateway;

    public CodeSearchTool(SandboxToolGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public String name() {
        return "code.search";
    }

    @Override
    public ToolRiskLevel riskLevel() {
        return ToolRiskLevel.SANDBOXED;
    }

    @Override
    public String inputSchema() {
        return "{\"type\":\"object\",\"properties\":{"
                + "\"pattern\":{\"type\":\"string\"}},\"required\":[\"pattern\"]}";
    }

    @Override
    public ToolResult<String> execute(ToolContext context, Input input) {
        if (input == null || input.pattern() == null || input.pattern().isBlank()) {
            return ToolResult.failure("code.search requires a pattern", 0L);
        }
        return gateway.run(context, name(), List.of(input.pattern()));
    }
}

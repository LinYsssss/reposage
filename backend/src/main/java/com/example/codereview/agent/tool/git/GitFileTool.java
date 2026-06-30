package com.example.codereview.agent.tool.git;

import com.example.codereview.agent.tool.AgentTool;
import com.example.codereview.agent.tool.ToolContext;
import com.example.codereview.agent.tool.ToolResult;
import com.example.codereview.agent.tool.ToolRiskLevel;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * {@code git.file} — reads a single file's content (bounded) from the prepared repository in the
 * sandbox. The path is confined to the workspace by the runner.
 */
@Component
public class GitFileTool implements AgentTool<GitFileTool.Input, String> {

    public record Input(String path) {
    }

    private final SandboxToolGateway gateway;

    public GitFileTool(SandboxToolGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public String name() {
        return "git.file";
    }

    @Override
    public ToolRiskLevel riskLevel() {
        return ToolRiskLevel.SANDBOXED;
    }

    @Override
    public String inputSchema() {
        return "{\"type\":\"object\",\"properties\":{"
                + "\"path\":{\"type\":\"string\"}},\"required\":[\"path\"]}";
    }

    @Override
    public ToolResult<String> execute(ToolContext context, Input input) {
        if (input == null || input.path() == null || input.path().isBlank()) {
            return ToolResult.failure("git.file requires a path", 0L);
        }
        return gateway.run(context, name(), List.of(input.path()));
    }
}

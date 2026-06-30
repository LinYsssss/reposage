package com.example.codereview.agent.tool.git;

import com.example.codereview.agent.tool.AgentTool;
import com.example.codereview.agent.tool.ToolContext;
import com.example.codereview.agent.tool.ToolResult;
import com.example.codereview.agent.tool.ToolRiskLevel;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * {@code git.diff} — produces the diff between two refs (or the working tree) in the sandbox.
 * Read-only but {@link ToolRiskLevel#SANDBOXED} because it runs git inside the constrained runner.
 */
@Component
public class GitDiffTool implements AgentTool<GitDiffTool.Input, String> {

    public record Input(String baseRef, String headRef) {
    }

    private final SandboxToolGateway gateway;

    public GitDiffTool(SandboxToolGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public String name() {
        return "git.diff";
    }

    @Override
    public ToolRiskLevel riskLevel() {
        return ToolRiskLevel.SANDBOXED;
    }

    @Override
    public String inputSchema() {
        return "{\"type\":\"object\",\"properties\":{"
                + "\"baseRef\":{\"type\":\"string\"},\"headRef\":{\"type\":\"string\"}}}";
    }

    @Override
    public ToolResult<String> execute(ToolContext context, Input input) {
        List<String> args = new ArrayList<>();
        if (input != null && input.baseRef() != null && !input.baseRef().isBlank()) {
            args.add(input.baseRef());
        }
        if (input != null && input.headRef() != null && !input.headRef().isBlank()) {
            args.add(input.headRef());
        }
        return gateway.run(context, name(), args);
    }
}

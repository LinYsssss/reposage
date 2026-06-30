package com.example.codereview.agent.tool.git;

import com.example.codereview.agent.tool.ToolContext;
import com.example.codereview.agent.tool.ToolResult;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Default gateway used until a real runner dispatcher is wired in. It performs no execution and
 * fails clearly, so a misconfigured deployment never silently returns empty tool output. A real
 * dispatcher can replace it with a {@code @Primary} {@link SandboxToolGateway} bean.
 */
@Component
public class NoopSandboxToolGateway implements SandboxToolGateway {

    @Override
    public ToolResult<String> run(ToolContext context, String commandId, List<String> args) {
        return ToolResult.failure("sandbox dispatch not configured for " + commandId, 0L);
    }
}

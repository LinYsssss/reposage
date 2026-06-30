package com.example.codereview.agent.tool.git;

import com.example.codereview.agent.tool.ToolContext;
import com.example.codereview.agent.tool.ToolResult;
import java.util.List;

/**
 * Dispatches a whitelisted read-only repository command into the sandbox runner and returns its
 * bounded text output. Tools never touch the repository directly — they describe a command id plus
 * arguments and let the trusted runner execute it under the container policy.
 */
public interface SandboxToolGateway {

    ToolResult<String> run(ToolContext context, String commandId, List<String> args);
}

package com.example.codereview.agent.tool.git;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.codereview.agent.tool.AgentTool;
import com.example.codereview.agent.tool.AgentToolRegistry;
import com.example.codereview.agent.tool.ToolContext;
import com.example.codereview.agent.tool.ToolResult;
import com.example.codereview.agent.tool.ToolRiskLevel;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The git/code read tools register under the expected ids, are sandboxed, and dispatch the right
 * command id + arguments to the gateway.
 */
class RepositoryToolsTest {

    private final RecordingGateway gateway = new RecordingGateway();
    private final GitDiffTool gitDiff = new GitDiffTool(gateway);
    private final GitFileTool gitFile = new GitFileTool(gateway);
    private final CodeSearchTool codeSearch = new CodeSearchTool(gateway);
    private final ToolContext context = new ToolContext(1L, 2L, 3L, 4L, "sha", "ws-1");

    @Test
    void registersGitDiffGitFileAndCodeSearchAsSandboxedTools() {
        AgentToolRegistry registry = new AgentToolRegistry(List.of(gitDiff, gitFile, codeSearch));

        assertThat(registry.size()).isEqualTo(3);
        for (String name : List.of("git.diff", "git.file", "code.search")) {
            AgentTool<?, ?> tool = registry.getTool(name).orElseThrow();
            assertThat(tool.riskLevel()).isEqualTo(ToolRiskLevel.SANDBOXED);
            assertThat(tool.inputSchema()).isNotNull();
        }
    }

    @Test
    void gitFileDispatchesPathArgument() {
        ToolResult<String> result = gitFile.execute(context, new GitFileTool.Input("src/Main.java"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(gateway.lastCommandId).isEqualTo("git.file");
        assertThat(gateway.lastArgs).containsExactly("src/Main.java");
    }

    @Test
    void codeSearchDispatchesPatternArgument() {
        codeSearch.execute(context, new CodeSearchTool.Input("TODO"));

        assertThat(gateway.lastCommandId).isEqualTo("code.search");
        assertThat(gateway.lastArgs).containsExactly("TODO");
    }

    @Test
    void gitDiffDispatchesBothRefsWhenPresent() {
        gitDiff.execute(context, new GitDiffTool.Input("main", "feature"));

        assertThat(gateway.lastCommandId).isEqualTo("git.diff");
        assertThat(gateway.lastArgs).containsExactly("main", "feature");
    }

    @Test
    void toolsRejectBlankInputWithoutDispatching() {
        assertThat(gitFile.execute(context, new GitFileTool.Input(" ")).isSuccess()).isFalse();
        assertThat(codeSearch.execute(context, new CodeSearchTool.Input(null)).isSuccess()).isFalse();
        assertThat(gateway.lastCommandId).isNull();
    }

    private static final class RecordingGateway implements SandboxToolGateway {
        private String lastCommandId;
        private List<String> lastArgs;

        @Override
        public ToolResult<String> run(ToolContext context, String commandId, List<String> args) {
            this.lastCommandId = commandId;
            this.lastArgs = new ArrayList<>(args);
            return ToolResult.success("output of " + commandId, 1L);
        }
    }
}

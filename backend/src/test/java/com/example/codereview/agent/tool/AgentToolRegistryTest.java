package com.example.codereview.agent.tool;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class AgentToolRegistryTest {

    // Test tool implementations
    static class GitDiffTool implements AgentTool<String, String> {
        @Override
        public String name() {
            return "git.diff";
        }

        @Override
        public ToolRiskLevel riskLevel() {
            return ToolRiskLevel.READ_ONLY;
        }

        @Override
        public ToolResult<String> execute(ToolContext context, String input) {
            return ToolResult.success("diff output", 100L);
        }
    }

    static class CodeSearchTool implements AgentTool<String, String> {
        @Override
        public String name() {
            return "code.search";
        }

        @Override
        public ToolRiskLevel riskLevel() {
            return ToolRiskLevel.READ_ONLY;
        }

        @Override
        public ToolResult<String> execute(ToolContext context, String input) {
            return ToolResult.success("search results", 50L);
        }
    }

    static class ScmCommentTool implements AgentTool<String, Void> {
        @Override
        public String name() {
            return "scm.comment";
        }

        @Override
        public ToolRiskLevel riskLevel() {
            return ToolRiskLevel.APPROVAL_REQUIRED;
        }

        @Override
        public ToolResult<Void> execute(ToolContext context, String input) {
            return ToolResult.success(null, 200L);
        }
    }

    @Test
    void registryConstruction_shouldIndexAllTools() {
        var registry = new AgentToolRegistry(List.of(new GitDiffTool(), new CodeSearchTool()));

        assertThat(registry.size()).isEqualTo(2);
        assertThat(registry.getTool("git.diff")).isPresent();
        assertThat(registry.getTool("code.search")).isPresent();
    }

    @Test
    void duplicateToolName_shouldThrowException() {
        var tool1 = new GitDiffTool();
        var tool2 = new GitDiffTool();

        assertThatThrownBy(() -> new AgentToolRegistry(List.of(tool1, tool2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate tool name");
    }

    @Test
    void unknownTool_shouldReturnEmpty() {
        var registry = new AgentToolRegistry(List.of(new GitDiffTool()));

        assertThat(registry.getTool("unknown.tool")).isEmpty();
    }

    @Test
    void approvalRequiredTool_shouldBeRegistered() {
        var registry = new AgentToolRegistry(List.of(new ScmCommentTool()));

        var tool = registry.getTool("scm.comment");
        assertThat(tool).isPresent();
        assertThat(tool.get().riskLevel()).isEqualTo(ToolRiskLevel.APPROVAL_REQUIRED);
    }

    @Test
    void toolExecution_shouldReturnResult() {
        var tool = new GitDiffTool();
        var context = new ToolContext(1L, 2L, 3L, 4L, "abc123", "ws-1");

        var result = tool.execute(context, "HEAD~1..HEAD");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).isEqualTo("diff output");
        assertThat(result.getDurationMs()).isEqualTo(100L);
    }
}

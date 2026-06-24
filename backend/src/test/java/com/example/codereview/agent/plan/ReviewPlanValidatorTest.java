package com.example.codereview.agent.plan;

import com.example.codereview.agent.tool.AgentTool;
import com.example.codereview.agent.tool.AgentToolRegistry;
import com.example.codereview.agent.tool.ToolContext;
import com.example.codereview.agent.tool.ToolResult;
import com.example.codereview.agent.tool.ToolRiskLevel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class ReviewPlanValidatorTest {

    static class ReadOnlyTool implements AgentTool<String, String> {
        private final String name;

        ReadOnlyTool(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public ToolRiskLevel riskLevel() {
            return ToolRiskLevel.READ_ONLY;
        }

        @Override
        public ToolResult<String> execute(ToolContext context, String input) {
            return ToolResult.success("output", 10L);
        }
    }

    static class ApprovalTool implements AgentTool<String, Void> {
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
            return ToolResult.success(null, 100L);
        }
    }

    @Test
    void validPlan_shouldPass() {
        var registry = new AgentToolRegistry(List.of(
                new ReadOnlyTool("git.diff"),
                new ReadOnlyTool("code.search")
        ));
        var validator = new ReviewPlanValidator(registry);

        var plan = new ReviewPlan(List.of(
                new ReviewPlan.PlanItem("git.diff", null, "get changes", "diff text"),
                new ReviewPlan.PlanItem("code.search", null, "find usages", "search results")
        ));

        var result = validator.validate(plan);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void emptyPlan_shouldFail() {
        var registry = new AgentToolRegistry(List.of());
        var validator = new ReviewPlanValidator(registry);

        var plan = new ReviewPlan(List.of());

        var result = validator.validate(plan);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).contains("Plan is empty");
    }

    @Test
    void unknownTool_shouldFail() {
        var registry = new AgentToolRegistry(List.of(new ReadOnlyTool("git.diff")));
        var validator = new ReviewPlanValidator(registry);

        var plan = new ReviewPlan(List.of(
                new ReviewPlan.PlanItem("unknown.tool", null, "do something", "evidence")
        ));

        var result = validator.validate(plan);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("Unknown tool"));
    }

    @Test
    void approvalToolNotLast_shouldFail() {
        var registry = new AgentToolRegistry(List.of(
                new ApprovalTool(),
                new ReadOnlyTool("git.diff")
        ));
        var validator = new ReviewPlanValidator(registry);

        var plan = new ReviewPlan(List.of(
                new ReviewPlan.PlanItem("scm.comment", null, "post comment", "comment posted"),
                new ReviewPlan.PlanItem("git.diff", null, "get changes", "diff text")
        ));

        var result = validator.validate(plan);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("after approval-required"));
    }

    @Test
    void approvalToolLast_shouldPass() {
        var registry = new AgentToolRegistry(List.of(
                new ReadOnlyTool("git.diff"),
                new ApprovalTool()
        ));
        var validator = new ReviewPlanValidator(registry);

        var plan = new ReviewPlan(List.of(
                new ReviewPlan.PlanItem("git.diff", null, "get changes", "diff text"),
                new ReviewPlan.PlanItem("scm.comment", null, "post comment", "comment posted")
        ));

        var result = validator.validate(plan);

        assertThat(result.isValid()).isTrue();
    }
}

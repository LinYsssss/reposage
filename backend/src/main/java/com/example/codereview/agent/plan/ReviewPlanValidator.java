package com.example.codereview.agent.plan;

import com.example.codereview.agent.tool.AgentToolRegistry;
import com.example.codereview.agent.tool.ToolRiskLevel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates model-generated review plans before execution.
 */
@Component
public class ReviewPlanValidator {

    private final AgentToolRegistry toolRegistry;

    public ReviewPlanValidator(AgentToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;

        private ValidationResult(boolean valid, List<String> errors) {
            this.valid = valid;
            this.errors = errors;
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, List.of());
        }

        public static ValidationResult invalid(List<String> errors) {
            return new ValidationResult(false, errors);
        }

        public boolean isValid() {
            return valid;
        }

        public List<String> getErrors() {
            return errors;
        }
    }

    public ValidationResult validate(ReviewPlan plan) {
        List<String> errors = new ArrayList<>();

        if (plan == null || plan.getItems() == null || plan.getItems().isEmpty()) {
            errors.add("Plan is empty");
            return ValidationResult.invalid(errors);
        }

        Set<String> seenTools = new HashSet<>();
        boolean hasApprovalRequired = false;

        for (int i = 0; i < plan.getItems().size(); i++) {
            ReviewPlan.PlanItem item = plan.getItems().get(i);

            // Tool must exist
            var tool = toolRegistry.getTool(item.getToolName());
            if (tool.isEmpty()) {
                errors.add(String.format("Item %d: Unknown tool '%s'", i, item.getToolName()));
                continue;
            }

            // Approval-required tools must come after all other work
            if (tool.get().riskLevel() == ToolRiskLevel.APPROVAL_REQUIRED) {
                hasApprovalRequired = true;
            } else if (hasApprovalRequired) {
                errors.add(String.format("Item %d: Non-approval tool '%s' after approval-required tool",
                        i, item.getToolName()));
            }

            seenTools.add(item.getToolName());
        }

        return errors.isEmpty() ? ValidationResult.valid() : ValidationResult.invalid(errors);
    }
}

package com.example.codereview.agent.plan;

import java.util.List;

/**
 * Model-generated review plan with ordered tool invocations.
 */
public class ReviewPlan {

    private List<PlanItem> items;

    public ReviewPlan() {
    }

    public ReviewPlan(List<PlanItem> items) {
        this.items = items;
    }

    public List<PlanItem> getItems() {
        return items;
    }

    public void setItems(List<PlanItem> items) {
        this.items = items;
    }

    /**
     * Single tool invocation in the plan.
     */
    public static class PlanItem {
        private String toolName;
        private Object arguments;
        private String purpose;
        private String expectedEvidence;

        public PlanItem() {
        }

        public PlanItem(String toolName, Object arguments, String purpose, String expectedEvidence) {
            this.toolName = toolName;
            this.arguments = arguments;
            this.purpose = purpose;
            this.expectedEvidence = expectedEvidence;
        }

        public String getToolName() {
            return toolName;
        }

        public void setToolName(String toolName) {
            this.toolName = toolName;
        }

        public Object getArguments() {
            return arguments;
        }

        public void setArguments(Object arguments) {
            this.arguments = arguments;
        }

        public String getPurpose() {
            return purpose;
        }

        public void setPurpose(String purpose) {
            this.purpose = purpose;
        }

        public String getExpectedEvidence() {
            return expectedEvidence;
        }

        public void setExpectedEvidence(String expectedEvidence) {
            this.expectedEvidence = expectedEvidence;
        }
    }
}

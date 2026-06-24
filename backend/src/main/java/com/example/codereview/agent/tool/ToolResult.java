package com.example.codereview.agent.tool;

/**
 * Result of a tool invocation.
 */
public class ToolResult<O> {
    private final boolean success;
    private final O output;
    private final String errorMessage;
    private final long durationMs;

    private ToolResult(boolean success, O output, String errorMessage, long durationMs) {
        this.success = success;
        this.output = output;
        this.errorMessage = errorMessage;
        this.durationMs = durationMs;
    }

    public static <O> ToolResult<O> success(O output, long durationMs) {
        return new ToolResult<>(true, output, null, durationMs);
    }

    public static <O> ToolResult<O> failure(String errorMessage, long durationMs) {
        return new ToolResult<>(false, null, errorMessage, durationMs);
    }

    public boolean isSuccess() {
        return success;
    }

    public O getOutput() {
        return output;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public long getDurationMs() {
        return durationMs;
    }
}

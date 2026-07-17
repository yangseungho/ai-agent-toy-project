package com.example.aiagent.planner;

import lombok.Getter;

/**
 * 실행 계획의 한 단계.
 */
@Getter
public class PlanStep {

    public enum Action {
        /** 특정 Tool 을 호출한다 */
        CALL_TOOL,
        /** 더 이상 호출할 Tool 이 없다 → 답변 생성 단계로 */
        FINISH
    }

    private final Action action;

    /** CALL_TOOL 일 때 호출할 Tool 이름 (FINISH 면 null) */
    private final String toolName;

    /** 이 단계를 선택한 이유 (관측/교육용) */
    private final String reason;

    private PlanStep(Action action, String toolName, String reason) {
        this.action = action;
        this.toolName = toolName;
        this.reason = reason;
    }

    public static PlanStep callTool(String toolName, String reason) {
        return new PlanStep(Action.CALL_TOOL, toolName, reason);
    }

    public static PlanStep finish(String reason) {
        return new PlanStep(Action.FINISH, null, reason);
    }

    public boolean isFinish() {
        return action == Action.FINISH;
    }

    @Override
    public String toString() {
        return isFinish() ? "FINISH(" + reason + ")" : "CALL_TOOL(" + toolName + " — " + reason + ")";
    }
}

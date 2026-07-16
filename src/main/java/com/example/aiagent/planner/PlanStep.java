package com.example.aiagent.planner;

import lombok.Getter;

/**
 * 실행 계획의 한 단계.
 *
 * <p>두 종류 중 하나이다.</p>
 * <ul>
 *     <li>{@code CALL_TOOL} : 특정 Tool 을 호출한다 ({@link #toolName} 가 채워짐)</li>
 *     <li>{@code FINISH}    : 더 이상 호출할 Tool 이 없으니 답변 생성 단계로 넘어간다</li>
 * </ul>
 */
@Getter
public class PlanStep {

    /** 단계의 종류 */
    public enum Action {
        CALL_TOOL,
        FINISH
    }

    private final Action action;

    /** action 이 CALL_TOOL 일 때 호출할 Tool 이름 (FINISH 면 null) */
    private final String toolName;

    private PlanStep(Action action, String toolName) {
        this.action = action;
        this.toolName = toolName;
    }

    /** Tool 호출 단계를 만든다. */
    public static PlanStep callTool(String toolName) {
        return new PlanStep(Action.CALL_TOOL, toolName);
    }

    /** 종료(답변 생성) 단계를 만든다. */
    public static PlanStep finish() {
        return new PlanStep(Action.FINISH, null);
    }

    public boolean isFinish() {
        return action == Action.FINISH;
    }

    @Override
    public String toString() {
        return isFinish() ? "FINISH" : "CALL_TOOL(" + toolName + ")";
    }
}

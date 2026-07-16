package com.example.aiagent.planner;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Planner 가 만든 실행 계획.
 *
 * <p>어떤 Tool 들을 어떤 순서로 호출할지에 대한 "처음 세운 계획"이다.
 * 실제 실행은 Agent Loop 에서 이루어지며, Loop 는 매 반복마다 Planner 에게
 * 다음 단계를 다시 물어본다({@link Planner#decideNextStep}). 따라서 이 계획은
 * 고정된 스크립트가 아니라 "초기 청사진"에 가깝다.</p>
 */
@Getter
public class ExecutionPlan {

    private final List<PlanStep> steps = new ArrayList<>();

    public void addStep(PlanStep step) {
        steps.add(step);
    }

    /** 계획을 사람이 읽기 좋은 문자열로 표현한다. (로그/교육용) */
    public String describe() {
        StringBuilder sb = new StringBuilder("ExecutionPlan:\n");
        int stepNumber = 1;
        for (PlanStep step : steps) {
            sb.append("  Step").append(stepNumber++).append(" ").append(step).append("\n");
        }
        return sb.toString().trim();
    }
}

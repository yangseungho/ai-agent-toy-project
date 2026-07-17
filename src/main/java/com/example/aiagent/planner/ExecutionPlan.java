package com.example.aiagent.planner;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Planner 가 처음 세운 실행 계획(청사진).
 *
 * <p>실제 실행은 Agent Loop 가 하며, Loop 는 매 반복마다 Planner 에게 다음 단계를
 * 다시 물어본다({@link Planner#decideNextStep}). Tool 결과에 따라 계획이 바뀔 수 있으므로
 * 이 객체는 고정 스크립트가 아니라 "초기 계획"일 뿐이다.</p>
 */
@Getter
public class ExecutionPlan {

    private final List<PlanStep> steps = new ArrayList<>();

    public void addStep(PlanStep step) {
        steps.add(step);
    }

    /** 사람이 읽을 수 있는 형태로 계획을 출력한다. */
    public String describe() {
        StringBuilder sb = new StringBuilder("ExecutionPlan:");
        int stepNumber = 1;
        for (PlanStep step : steps) {
            sb.append("\n  Step").append(stepNumber++).append(" ").append(step);
        }
        return sb.toString();
    }
}

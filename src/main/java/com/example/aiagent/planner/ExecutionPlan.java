package com.example.aiagent.planner;

import lombok.Getter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 이번 턴의 실행 계획. <b>상태를 가진 객체</b>이며 턴당 하나만 만들어진다.
 *
 * <p>예전에는 계획이 "처음 한 번 만들고 로그만 찍는 청사진"이었고, 실제 실행은
 * Planner 가 매 반복마다 필요한 Tool 목록을 <b>처음부터 다시 계산</b>해서 진행했다.
 * 즉 "무엇을 할지"에 대한 진실이 두 군데 있었고, 로그에 남은 계획과 실제 실행이
 * 얼마든지 어긋날 수 있었다.</p>
 *
 * <p>지금은 계획이 유일한 진실이다. Agent Loop 는 이 객체를 소비하며 진행하고
 * ({@link #markCompleted}), 상황이 바뀌면 계획을 수정한다({@link #drop}).
 * 그래서 {@link #describe()} 는 언제 찍어도 실제 실행 상태와 일치한다.</p>
 */
@Getter
public class ExecutionPlan {

    /** 처음 세운 계획: 실행해야 할 Tool 이름 (감지된 모든 의도의 합집합) */
    private final Set<String> requiredTools;

    /** 실행이 끝난 Tool */
    private final Set<String> completed = new LinkedHashSet<>();

    /** 실행하지 않기로 한 Tool → 그 이유 (관측용) */
    private final Map<String, String> dropped = new LinkedHashMap<>();

    public ExecutionPlan(List<String> requiredTools) {
        this.requiredTools = new LinkedHashSet<>(requiredTools);
    }

    /** 아직 실행하지도, 포기하지도 않은 Tool. */
    public List<String> pending() {
        List<String> pending = new ArrayList<>();
        for (String toolName : requiredTools) {
            if (!completed.contains(toolName) && !dropped.containsKey(toolName)) {
                pending.add(toolName);
            }
        }
        return pending;
    }

    public void markCompleted(String toolName) {
        completed.add(toolName);
    }

    /**
     * 이 Tool 은 실행하지 않는다고 계획을 수정한다.
     *
     * <p>계획이 바뀌는 대표적인 경우:</p>
     * <ul>
     *     <li>선행 Tool 이 값을 못 찾아 필요한 입력을 끝내 얻지 못함
     *         (주문이 없으면 배송/쿠폰 조회는 의미가 없다)</li>
     *     <li>Tool 이 레지스트리에 없음 (MCP 서버 미연결)</li>
     * </ul>
     * <p>이유를 함께 남기는 이유는, 답변에 특정 정보가 빠진 원인을 나중에 추적할 수
     * 있어야 하기 때문이다.</p>
     */
    public void drop(String toolName, String reason) {
        dropped.put(toolName, reason);
    }

    /** 더 할 일이 없는가? */
    public boolean isComplete() {
        return pending().isEmpty();
    }

    /** 사람이 읽을 수 있는 형태로 현재 계획 상태를 출력한다. */
    public String describe() {
        StringBuilder sb = new StringBuilder("ExecutionPlan:");
        if (requiredTools.isEmpty()) {
            sb.append("\n  (조회 없이 바로 답변)");
            return sb.toString();
        }
        for (String toolName : requiredTools) {
            String state = completed.contains(toolName) ? "완료"
                    : dropped.containsKey(toolName) ? "생략(" + dropped.get(toolName) + ")"
                    : "대기";
            sb.append("\n  - ").append(toolName).append(" : ").append(state);
        }
        return sb.toString();
    }
}

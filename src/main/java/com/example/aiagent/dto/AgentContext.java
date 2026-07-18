package com.example.aiagent.dto;

import com.example.aiagent.intent.Intent;
import com.example.aiagent.intent.IntentClassification;
import com.example.aiagent.memory.ChatMessage;
import com.example.aiagent.planner.ExecutionPlan;
import com.example.aiagent.tool.ToolContext;
import com.example.aiagent.tool.ToolResult;
import lombok.Getter;
import lombok.Setter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Agent 실행 중 상태(작업 메모리).
 *
 * <p>파이프라인 전체를 관통하는 가변 객체. Planner 는 여기 쌓인 Tool 결과를 보고
 * 다음 행동을 정하고, PromptBuilder/Validator 는 이 결과를 근거로 삼는다.</p>
 *
 * <p>{@link ToolContext} 를 구현하여 Tool 에게는 <b>필요한 것만</b> 노출한다.</p>
 *
 * <h2>동시성</h2>
 * <p>같은 wave 의 Tool 들은 여러 스레드에서 동시에 이 객체를 <b>읽는다</b>.
 * 쓰기({@link #addToolResult})는 wave 가 모두 끝난 뒤 Agent Loop 스레드가 단독으로
 * 수행하므로 읽기/쓰기가 겹치지 않는 것이 원칙이지만, 향후 실수로 이 규약이 깨져도
 * 자료구조가 깨지지는 않도록 동기화된 컬렉션을 쓴다. (요소 수가 한 자리라 비용은 무의미하다.)</p>
 */
@Getter
public class AgentContext implements ToolContext {

    /** 대화 세션 ID (Memory 조회/저장 키) */
    private final String conversationId;

    /** 고객 ID (DB 조회 키) */
    private final String customerId;

    /** 이번 턴 질문 */
    private final String question;

    /** LLM 의도 분류 결과 (복합 의도 포함) */
    private final IntentClassification intentClassification;

    /** Memory 에서 로드한 이전 대화 이력 */
    private final List<ChatMessage> history;

    /** 실행된 Tool 결과 (실행 순서 보존) */
    private final Map<String, ToolResult> toolResults =
            Collections.synchronizedMap(new LinkedHashMap<>());

    /** Tool 실행 순서 */
    private final List<String> executedToolOrder = new CopyOnWriteArrayList<>();

    /** 파이프라인 단계별 추적 로그 */
    private final List<String> trace = new CopyOnWriteArrayList<>();

    /**
     * 이번 턴의 실행 계획.
     *
     * <p>계획은 턴당 <b>한 번만</b> 세워 여기에 보관하고, Agent Loop 는 매 반복마다
     * 이 객체의 상태를 갱신하며 진행한다. 계획을 매번 다시 계산하지 않기 때문에
     * "로그에 찍힌 계획"과 "실제 실행"이 어긋날 수 없다.</p>
     */
    @Setter
    private ExecutionPlan plan;

    public AgentContext(String conversationId,
                        String customerId,
                        String question,
                        IntentClassification intentClassification,
                        List<ChatMessage> history) {
        this.conversationId = conversationId;
        this.customerId = customerId;
        this.question = question;
        this.intentClassification = intentClassification;
        this.history = List.copyOf(history);
    }

    // --- ToolContext 구현 ---------------------------------------------------

    @Override
    public String question() {
        return question;
    }

    @Override
    public String customerId() {
        return customerId;
    }

    @Override
    public ToolResult toolResult(String toolName) {
        return toolResults.get(toolName);
    }

    @Override
    public String input(String key) {
        synchronized (toolResults) {
            // 실행 순서대로 순회하므로 '가장 먼저 그 값을 만든 Tool' 이 이긴다.
            for (ToolResult result : toolResults.values()) {
                String value = result.get(key);
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    @Override
    public Set<String> availableInputs() {
        Set<String> keys = new LinkedHashSet<>();
        synchronized (toolResults) {
            for (ToolResult result : toolResults.values()) {
                keys.addAll(result.getData().keySet());
            }
        }
        return keys;
    }

    // --- 상태 변경 -----------------------------------------------------------

    public void addToolResult(ToolResult result) {
        toolResults.put(result.getToolName(), result);
        executedToolOrder.add(result.getToolName());
    }

    public boolean hasToolResult(String toolName) {
        return toolResults.containsKey(toolName);
    }

    public void log(String message) {
        trace.add(message);
    }

    // --- 편의 메서드 ---------------------------------------------------------

    /** 감지된 모든 의도. */
    public List<Intent> intents() {
        return intentClassification.intents();
    }

    /** 핵심 의도. */
    public Intent primaryIntent() {
        return intentClassification.primaryIntent();
    }
}

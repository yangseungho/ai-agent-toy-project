package com.example.aiagent.dto;

import com.example.aiagent.intent.Intent;
import com.example.aiagent.intent.IntentClassification;
import com.example.aiagent.memory.ChatMessage;
import com.example.aiagent.tool.ToolContext;
import com.example.aiagent.tool.ToolResult;
import lombok.Getter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 실행 중 상태(작업 메모리).
 *
 * <p>파이프라인 전체를 관통하는 가변 객체. Planner 는 여기 쌓인 Tool 결과를 보고
 * 다음 행동을 정하고, PromptBuilder/Validator 는 이 결과를 근거로 삼는다.</p>
 *
 * <p>{@link ToolContext} 를 구현하여 Tool 에게는 <b>필요한 것만</b> 노출한다.</p>
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
    private final Map<String, ToolResult> toolResults = new LinkedHashMap<>();

    /** Tool 실행 순서 */
    private final List<String> executedToolOrder = new ArrayList<>();

    /** 파이프라인 단계별 추적 로그 */
    private final List<String> trace = new ArrayList<>();

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

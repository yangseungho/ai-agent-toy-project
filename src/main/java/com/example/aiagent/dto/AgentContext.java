package com.example.aiagent.dto;

import com.example.aiagent.intent.Intent;
import com.example.aiagent.tool.ToolResult;
import lombok.Getter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 실행 중 상태를 담는 컨텍스트(작업 메모리).
 *
 * <p>파이프라인 전체를 관통하며 흐르는 가변(mutable) 상태 객체이다.
 * Planner 는 여기에 쌓인 Tool 결과를 보고 다음 행동을 결정하고,
 * PromptBuilder/Validator 는 이 결과를 읽어 각자의 일을 한다.</p>
 */
@Getter
public class AgentContext {

    /** 사용자 질문 */
    private final String question;

    /** 분류된 Intent */
    private final Intent intent;

    /** 실행된 Tool 결과: toolName → ToolResult (실행 순서 보존) */
    private final Map<String, ToolResult> toolResults = new LinkedHashMap<>();

    /** Tool 이 실행된 순서 (교육용 추적) */
    private final List<String> executedToolOrder = new ArrayList<>();

    /** 파이프라인 단계별 로그 */
    private final List<String> trace = new ArrayList<>();

    public AgentContext(String question, Intent intent) {
        this.question = question;
        this.intent = intent;
    }

    /** Tool 실행 결과를 컨텍스트에 기록한다. */
    public void addToolResult(ToolResult result) {
        toolResults.put(result.getToolName(), result);
        executedToolOrder.add(result.getToolName());
    }

    /** 특정 Tool 이 이미 실행되었는지 여부. */
    public boolean hasToolResult(String toolName) {
        return toolResults.containsKey(toolName);
    }

    /** 특정 Tool 의 결과를 반환한다. 없으면 null. */
    public ToolResult getToolResult(String toolName) {
        return toolResults.get(toolName);
    }

    /** 파이프라인 로그를 남긴다. */
    public void log(String message) {
        trace.add(message);
    }
}

package com.example.aiagent.tool;

import java.util.HashMap;
import java.util.Map;

/**
 * 테스트용 ToolContext.
 *
 * <p>외부 인프라가 아니라 <b>테스트 픽스처</b>이므로 Mock 이 아닌 단순 구현체로 둔다.
 * (Mockito 로 매번 스텁하는 것보다 읽기 쉽다.)</p>
 */
class StubToolContext implements ToolContext {

    private final String question;
    private final String customerId;
    private final Map<String, ToolResult> results = new HashMap<>();

    StubToolContext(String question, String customerId) {
        this.question = question;
        this.customerId = customerId;
    }

    /** 이전 Tool 결과를 미리 넣어둔다 (Tool 간 의존성 테스트용). */
    StubToolContext withResult(ToolResult result) {
        results.put(result.getToolName(), result);
        return this;
    }

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
        return results.get(toolName);
    }
}

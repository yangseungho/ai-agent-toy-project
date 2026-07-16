package com.example.aiagent.prompt;

import com.example.aiagent.dto.AgentContext;
import com.example.aiagent.tool.ToolResult;
import org.springframework.stereotype.Component;

/**
 * PromptBuilder.
 *
 * <p>Agent Loop 가 끝난 뒤, 컨텍스트에 쌓인 모든 Tool 결과를 모아 하나의 {@link Prompt}
 * 객체로 만든다. 이 Prompt 가 LLM 의 입력이 된다.</p>
 */
@Component
public class PromptBuilder {

    private static final String SYSTEM_INSTRUCTION =
            "당신은 쇼핑몰 고객센터 AI 상담원입니다. "
                    + "아래 CONTEXT(주문/배송/정책/쿠폰 정보)에 근거하여 사실만으로 답변하세요. "
                    + "CONTEXT 와 모순되는 내용을 말하지 마세요.";

    /** 컨텍스트의 Tool 결과들을 모아 Prompt 를 만든다. */
    public Prompt build(AgentContext context) {
        StringBuilder contextInfo = new StringBuilder();

        // 실행된 순서대로 각 Tool 의 요약을 나열한다.
        for (String toolName : context.getExecutedToolOrder()) {
            ToolResult result = context.getToolResult(toolName);
            contextInfo.append("- (").append(toolName).append(") ")
                    .append(result.getSummary()).append("\n");
        }

        if (contextInfo.length() == 0) {
            contextInfo.append("(수집된 도구 정보 없음)");
        }

        return new Prompt(
                SYSTEM_INSTRUCTION,
                context.getQuestion(),
                contextInfo.toString().trim(),
                null // Reflection 은 아직 없음
        );
    }
}

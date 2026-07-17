package com.example.aiagent.prompt;

import com.example.aiagent.dto.AgentContext;
import com.example.aiagent.tool.ToolResult;
import org.springframework.stereotype.Component;

/**
 * PromptBuilder — Tool 이 수집한 근거를 하나의 Prompt 로 조립한다.
 *
 * <p>Agent 품질의 상당 부분이 여기서 결정된다. 핵심 원칙 세 가지.</p>
 *
 * <ol>
 *   <li><b>근거를 명시적으로 구분해서 준다.</b> 어떤 정보가 '이 고객의 실제 데이터'이고
 *       어떤 것이 '회사 정책 문서'인지 나눠줘야 모델이 둘을 섞지 않는다.</li>
 *   <li><b>조회 실패를 숨기지 않는다.</b> Tool 이 실패하면 "확인 불가"라고 그대로 전달한다.
 *       이걸 빼먹으면 모델은 빈칸을 그럴듯하게 채워버린다(환각).</li>
 *   <li><b>추측 금지를 시스템 규칙으로 못 박는다.</b> 근거에 없는 내용은 말하지 말고
 *       모르면 모른다고 하도록 지시한다.</li>
 * </ol>
 */
@Component
public class PromptBuilder {

    private static final String SYSTEM_INSTRUCTION = """
            당신은 쇼핑몰 고객센터의 AI 상담원입니다.

            반드시 지켜야 할 규칙:
            1. 아래 [고객 실제 데이터]와 [관련 정책 문서]에 있는 내용만 근거로 답변하세요.
            2. 근거에 없는 사실을 추측하거나 지어내지 마세요.
            3. 특히 배송 상태는 반드시 [고객 실제 데이터]에 적힌 값 그대로만 말하세요.
               데이터에 없거나 조회에 실패한 정보는 "확인이 어렵다"고 솔직히 안내하세요.
            4. 정책과 고객의 실제 상태를 결합해 결론을 내리세요.
               (예: 정책상 배송 전 취소 가능 + 이 주문은 배송 전 → 취소 가능)
            5. 한국어로, 고객에게 말하듯 친절하고 간결하게 답하세요.
            6. 내부 시스템 용어(Tool 이름, enum 값 등)를 그대로 노출하지 말고
               자연스러운 한국어로 풀어서 설명하세요.
            """;

    /**
     * 컨텍스트의 Tool 결과 + 대화 이력을 모아 Prompt 를 만든다.
     */
    public Prompt build(AgentContext context) {
        StringBuilder userMessage = new StringBuilder();

        // 1) 이 고객의 실제 데이터 (DB / 외부 API 조회 결과)
        userMessage.append("[고객 실제 데이터]\n");
        String customerFacts = renderCustomerFacts(context);
        userMessage.append(customerFacts.isEmpty() ? "(조회된 데이터 없음)\n" : customerFacts);

        // 2) 정책 문서 (RAG 검색 결과) — 사실과 규칙을 분리해서 제시한다.
        String policies = renderPolicies(context);
        if (!policies.isEmpty()) {
            userMessage.append("\n[관련 정책 문서]\n").append(policies);
        }

        // 3) 이번 질문
        userMessage.append("\n[고객 질문]\n").append(context.getQuestion());

        return new Prompt(
                SYSTEM_INSTRUCTION,
                context.getHistory(),
                userMessage.toString(),
                null // Reflection 은 아직 없음
        );
    }

    /** 정책(RAG)을 제외한 Tool 결과 = 이 고객의 사실. */
    private String renderCustomerFacts(AgentContext context) {
        StringBuilder sb = new StringBuilder();

        for (String toolName : context.getExecutedToolOrder()) {
            if (com.example.aiagent.tool.ToolNames.POLICY_RAG.equals(toolName)) {
                continue; // 정책은 아래에서 따로 렌더링
            }
            ToolResult result = context.toolResult(toolName);
            if (result == null) {
                continue;
            }

            // 실패도 그대로 노출한다 — 모델이 빈칸을 지어내지 않도록.
            sb.append("- ").append(result.getSummary()).append("\n");
        }
        return sb.toString();
    }

    /** RAG 검색 결과. */
    private String renderPolicies(AgentContext context) {
        ToolResult ragResult = context.toolResult(com.example.aiagent.tool.ToolNames.POLICY_RAG);
        if (ragResult == null) {
            return "";
        }
        return ragResult.getSummary() + "\n";
    }
}

package com.example.aiagent.reflection;

import com.example.aiagent.dto.AgentContext;
import com.example.aiagent.llm.LlmClient;
import com.example.aiagent.prompt.Prompt;
import com.example.aiagent.tool.ToolNames;
import com.example.aiagent.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * ReflectionEngine — 검증에 실패한 답변을 스스로 교정하게 만든다.
 *
 * <p>Validator 가 모순을 잡아냈을 때 그냥 에러를 내면 사용자는 아무 답도 못 받는다.
 * 대신 <b>무엇이 왜 틀렸는지</b>를 알려주고 다시 생성하게 하면 대부분 교정된다.
 * 이것이 Reflection 이다.</p>
 *
 * <p>핵심은 교정 지시를 <b>구체적으로</b> 주는 것이다. "다시 답변하세요"가 아니라
 * "실제 배송 상태는 NOT_SHIPPED 다. 이 사실과 모순되게 말하지 마라"처럼
 * 실제 데이터를 다시 못 박아준다.</p>
 *
 * <p>재시도 횟수는 반드시 제한한다({@code agent.loop.max-reflection-retries}).
 * 무한 재시도는 비용과 지연이 폭발하고, 모델이 계속 실패하면 어차피 실패한다.</p>
 */
@Slf4j
@Component
public class ReflectionEngine {

    private final LlmClient llmClient;

    public ReflectionEngine(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    /**
     * 교정 지시를 붙인 Prompt 로 LLM 을 다시 호출한다.
     *
     * @param context          실제 데이터가 담긴 컨텍스트
     * @param originalPrompt   최초 Prompt
     * @param validationReason 검증 실패 사유
     * @return 교정된 답변
     */
    public String reflectAndRetry(AgentContext context, Prompt originalPrompt, String validationReason) {
        String note = buildReflectionNote(context, validationReason);

        log.debug("[Reflection] 교정 지시:\n{}", note);

        Prompt corrected = originalPrompt.withReflectionNote(note);
        return llmClient.complete(corrected);
    }

    /**
     * 실패 사유 + 실제 데이터를 근거로 교정 지시문을 만든다.
     */
    private String buildReflectionNote(AgentContext context, String validationReason) {
        StringBuilder note = new StringBuilder();

        note.append("직전에 생성한 답변이 실제 데이터와 모순되어 사용할 수 없습니다.\n");
        note.append("문제: ").append(validationReason).append("\n\n");
        note.append("반드시 아래 확인된 사실만을 근거로 다시 답변하세요. 추측하지 마세요.\n");

        // 실제 배송 상태를 다시 명시적으로 못 박는다.
        ToolResult shipping = context.toolResult(ToolNames.SHIPPING);
        if (shipping != null) {
            if (!shipping.isSuccess()) {
                note.append("- 배송 정보: 조회 실패. 배송 상태를 단정하지 말고 '현재 확인이 어렵다'고 안내할 것.\n");
            } else if (!shipping.hasData()) {
                note.append("- 배송 정보: 아직 배송 정보가 생성되지 않음(출고 전). 배송이 시작되었다고 말하지 말 것.\n");
            } else {
                note.append("- 실제 배송 상태: ").append(shipping.get("status"))
                        .append(" — 이 값과 모순되는 표현을 절대 사용하지 말 것.\n");
            }
        }

        // 실제 주문번호도 다시 명시한다.
        ToolResult order = context.toolResult(ToolNames.ORDER);
        if (order != null && order.get("orderId") != null) {
            note.append("- 실제 주문번호: ").append(order.get("orderId"))
                    .append(" — 다른 주문번호를 언급하지 말 것.\n");
        }

        return note.toString();
    }
}

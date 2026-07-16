package com.example.aiagent.reflection;

import com.example.aiagent.dto.AgentContext;
import com.example.aiagent.llm.LLMClient;
import com.example.aiagent.prompt.Prompt;
import com.example.aiagent.tool.ToolNames;
import com.example.aiagent.tool.ToolResult;
import org.springframework.stereotype.Component;

/**
 * ReflectionEngine.
 *
 * <p>Validation 이 실패했을 때 실행된다. 실패 사유와 실제 데이터를 근거로 Prompt 에
 * "교정 지시(reflection note)"를 덧붙인 뒤 LLM 을 <b>다시 한 번</b> 호출한다.</p>
 *
 * <p>요구사항: Retry 는 최대 1회. (여기서는 이 메서드가 호출되면 정확히 1번 재호출한다.)</p>
 */
@Component
public class ReflectionEngine {

    private final LLMClient llmClient;

    public ReflectionEngine(LLMClient llmClient) {
        this.llmClient = llmClient;
    }

    /**
     * 교정 지시를 담은 Prompt 로 LLM 을 1회 재호출하여 새 답변을 얻는다.
     *
     * @param context          실제 데이터가 담긴 컨텍스트
     * @param originalPrompt   최초 Prompt
     * @param validationReason Validation 실패 사유
     * @return 교정된 새 답변
     */
    public String reflectAndRetry(AgentContext context, Prompt originalPrompt, String validationReason) {
        String reflectionNote = buildReflectionNote(context, validationReason);

        // 최초 Prompt 에 교정 지시만 추가한 새 Prompt 로 다시 호출한다.
        Prompt correctedPrompt = originalPrompt.withReflectionNote(reflectionNote);
        return llmClient.complete(correctedPrompt);
    }

    /** 실제 배송 상태와 실패 사유를 바탕으로 교정 지시문을 만든다. */
    private String buildReflectionNote(AgentContext context, String validationReason) {
        StringBuilder note = new StringBuilder();
        note.append("이전 답변이 실제 데이터와 모순되어 거부되었습니다. 사유: ")
                .append(validationReason).append("\n");

        ToolResult shipping = context.getToolResult(ToolNames.SHIPPING);
        if (shipping != null) {
            note.append("실제 배송 상태는 ").append(shipping.get("status"))
                    .append(" 입니다. 이 사실과 모순되지 않게 다시 답변하세요.");
        } else {
            note.append("수집된 실제 데이터와 모순되지 않게 다시 답변하세요.");
        }
        return note.toString();
    }
}

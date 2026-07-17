package com.example.aiagent.intent;

import com.example.aiagent.llm.LlmClient;
import com.example.aiagent.llm.LlmException;
import com.example.aiagent.memory.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * LLM 기반 Intent 분류기.
 *
 * <p>키워드 규칙이 아니라 <b>모델</b>이 분류한다. 이유는 명확하다.</p>
 * <ul>
 *     <li>키워드 규칙은 복합 질의를 다루지 못한다. "취소하면 쿠폰 돌려받나요?" 에는
 *         '취소'와 '쿠폰'이 모두 있어 규칙 기반으로는 어떤 enum 하나를 골라야 할지 알 수 없다.</li>
 *     <li>모델은 여러 의도를 <b>동시에</b> 반환할 수 있고(다중 라벨), 표현이 달라도
 *         ("안왔어요", "언제 도착하나요", "배송 중인가요") 같은 의도로 묶어낸다.</li>
 * </ul>
 *
 * <p>구조화 출력(Structured Output)을 사용하므로 응답은 항상 {@link IntentClassification}
 * 스키마를 만족한다. 자유 텍스트를 파싱하다 깨지는 문제가 없다.</p>
 *
 * <p>이전 대화 이력도 함께 넘긴다. "그럼 취소해주세요" 처럼 앞 맥락이 있어야만
 * 의도를 알 수 있는 질문을 처리하기 위해서다.</p>
 */
@Slf4j
@Component
public class IntentClassifier {

    private static final String SYSTEM_INSTRUCTION = """
            당신은 쇼핑몰 고객센터의 문의 의도 분류기입니다.
            사용자의 질문을 읽고 어떤 의도가 담겨 있는지 분류하세요.

            분류 규칙:
            1. 하나의 질문에 여러 의도가 있으면 intents 에 모두 담으세요. 절대 하나로 축약하지 마세요.
            2. primaryIntent 는 사용자가 최종적으로 답을 듣고 싶어 하는 것 하나만 고르세요.
            3. 이전 대화 이력이 주어지면 그 맥락을 반영해 분류하세요.
               (예: 이전에 배송 얘기를 하다가 "그럼 취소해주세요" 라고 하면 REFUND)
            4. 쇼핑몰 문의와 전혀 무관하면 UNKNOWN 으로 분류하고 confidence 를 낮게 주세요.

            의도 종류:
            - ORDER_STATUS: 주문 내역이나 주문 상태 확인
            - SHIPPING: 배송 상태, 도착 예정일, 운송장 문의
            - REFUND: 환불, 주문 취소 문의
            - COUPON: 쿠폰 사용 여부, 취소 시 쿠폰 복구 문의
            - POLICY: 정책/규정 자체에 대한 문의
            - ACCOUNT: 회원, 계정, 로그인 문의
            - UNKNOWN: 그 외
            """;

    private final LlmClient llmClient;

    public IntentClassifier(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    /**
     * 질문(및 이전 대화 맥락)을 분류한다.
     *
     * @param question 이번 턴 사용자 질문
     * @param history  이전 대화 이력 (없으면 빈 리스트)
     * @return 분류 결과. LLM 호출 실패 시에도 예외를 던지지 않고 UNKNOWN 을 반환하여
     *         파이프라인이 계속 진행되도록 한다.
     */
    public IntentClassification classify(String question, List<ChatMessage> history) {
        if (question == null || question.isBlank()) {
            return IntentClassification.unknown("질문이 비어 있습니다.");
        }

        String userMessage = buildUserMessage(question, history);

        try {
            IntentClassification result =
                    llmClient.completeStructured(SYSTEM_INSTRUCTION, userMessage, IntentClassification.class);

            log.debug("[IntentClassifier] intents={} primary={} confidence={} reason={}",
                    result.intents(), result.primaryIntent(), result.confidence(), result.reasoning());
            return result;

        } catch (LlmException e) {
            // 분류에 실패해도 전체 요청을 실패시키지 않는다.
            // UNKNOWN 으로 라우팅되어 기본 Workflow 가 처리한다. (Graceful degradation)
            log.warn("[IntentClassifier] LLM 분류 실패 → UNKNOWN 으로 처리합니다.", e);
            return IntentClassification.unknown("LLM 분류 실패: " + e.getMessage());
        }
    }

    /** 이전 대화 이력을 포함한 분류용 입력 메시지를 만든다. */
    private String buildUserMessage(String question, List<ChatMessage> history) {
        StringBuilder sb = new StringBuilder();

        if (history != null && !history.isEmpty()) {
            sb.append("[이전 대화]\n");
            for (ChatMessage message : history) {
                String speaker = (message.role() == ChatMessage.Role.USER) ? "고객" : "상담원";
                sb.append(speaker).append(": ").append(message.content()).append("\n");
            }
            sb.append("\n");
        }

        sb.append("[분류할 질문]\n").append(question);
        return sb.toString();
    }
}

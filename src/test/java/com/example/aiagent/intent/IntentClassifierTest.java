package com.example.aiagent.intent;

import com.example.aiagent.llm.LlmClient;
import com.example.aiagent.llm.LlmException;
import com.example.aiagent.memory.ChatMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * IntentClassifier 단위 테스트.
 *
 * <p>실제 Claude API 는 호출하지 않는다. {@link LlmClient} 를 Mockito 로 mocking 하여
 * "모델이 이렇게 응답했을 때 우리 코드가 올바르게 동작하는가"만 검증한다.</p>
 */
class IntentClassifierTest {

    private final LlmClient llmClient = mock(LlmClient.class);
    private final IntentClassifier classifier = new IntentClassifier(llmClient);

    @Test
    @DisplayName("복합 질의는 여러 의도를 모두 반환한다")
    void classifyCompositeQuestion() {
        IntentClassification stubbed = new IntentClassification(
                List.of(Intent.ORDER_STATUS, Intent.SHIPPING, Intent.REFUND, Intent.COUPON),
                Intent.REFUND,
                0.95,
                "배송 지연과 취소, 쿠폰 복구를 함께 묻고 있음");

        when(llmClient.completeStructured(anyString(), anyString(), eq(IntentClassification.class)))
                .thenReturn(stubbed);

        IntentClassification result = classifier.classify(
                "지난주 주문한 상품이 아직 안왔는데 취소하면 쿠폰도 돌려받을 수 있나요?", List.of());

        assertEquals(4, result.intents().size());
        assertTrue(result.isComposite());
        assertEquals(Intent.REFUND, result.primaryIntent());
    }

    @Test
    @DisplayName("이전 대화 이력이 분류 입력에 포함된다")
    void includesHistoryInClassificationInput() {
        when(llmClient.completeStructured(anyString(), anyString(), any()))
                .thenReturn(new IntentClassification(List.of(Intent.REFUND), Intent.REFUND, 0.9, "맥락상 취소"));

        List<ChatMessage> history = List.of(
                ChatMessage.user("제 배송 언제 오나요?"),
                ChatMessage.assistant("아직 출고 전입니다."));

        classifier.classify("그럼 취소해주세요", history);

        // LLM 에 넘긴 사용자 메시지에 이전 대화가 들어갔는지 확인한다.
        ArgumentCaptor<String> userMessageCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmClient).completeStructured(anyString(), userMessageCaptor.capture(), any());

        String sentMessage = userMessageCaptor.getValue();
        assertTrue(sentMessage.contains("제 배송 언제 오나요?"), "이전 질문이 포함되어야 한다");
        assertTrue(sentMessage.contains("아직 출고 전입니다."), "이전 답변이 포함되어야 한다");
        assertTrue(sentMessage.contains("그럼 취소해주세요"), "이번 질문이 포함되어야 한다");
    }

    @Test
    @DisplayName("LLM 호출이 실패해도 예외를 던지지 않고 UNKNOWN 으로 처리한다")
    void gracefullyDegradesOnLlmFailure() {
        when(llmClient.completeStructured(anyString(), anyString(), any()))
                .thenThrow(new LlmException("API 장애"));

        IntentClassification result = classifier.classify("취소하고 싶어요", List.of());

        assertEquals(Intent.UNKNOWN, result.primaryIntent());
        assertEquals(0.0, result.confidence());
    }

    @Test
    @DisplayName("빈 질문은 LLM 을 호출하지 않고 UNKNOWN 을 반환한다")
    void blankQuestionSkipsLlmCall() {
        IntentClassification result = classifier.classify("   ", List.of());

        assertEquals(Intent.UNKNOWN, result.primaryIntent());
        // 불필요한 API 비용을 쓰지 않아야 한다.
        verify(llmClient, org.mockito.Mockito.never()).completeStructured(anyString(), anyString(), any());
    }
}

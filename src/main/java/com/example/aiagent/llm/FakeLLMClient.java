package com.example.aiagent.llm;

import com.example.aiagent.prompt.Prompt;
import org.springframework.stereotype.Component;

/**
 * Mock LLM 클라이언트.
 *
 * <p>실제 모델을 호출하지 않고 Prompt 내용에 따라 고정된 응답을 반환한다.
 * 이 Fake 는 교육 목적상 <b>일부러</b> 두 가지 다른 응답을 낸다.</p>
 *
 * <ol>
 *     <li><b>최초 호출</b> (Reflection 지시 없음): 실제 배송 상태(NOT_SHIPPED)를
 *         무시하고 "이미 배송이 완료되었다"고 <b>잘못</b> 답한다. → LLM 환각(hallucination)
 *         상황을 재현하여 Validator 가 이를 잡아내는 과정을 보여준다.</li>
 *     <li><b>Reflection 재호출</b> (Reflection 지시 있음): 교정 지시를 반영하여
 *         배송 상태와 모순되지 않는 <b>올바른</b> 답변을 반환한다.</li>
 * </ol>
 */
@Component
public class FakeLLMClient implements LLMClient {

    @Override
    public String complete(Prompt prompt) {
        // Reflection 단계에서 교정 지시가 들어오면 '올바른' 답변을 반환한다.
        if (prompt.hasReflectionNote()) {
            return "확인 결과 고객님의 상품은 아직 배송이 시작되지 않았습니다(출발 전). "
                    + "배송 전이므로 주문을 취소하실 수 있으며, 사용하신 쿠폰은 취소 시 "
                    + "환불 정책에 따라 복구되어 다시 사용하실 수 있습니다.";
        }

        // 최초 호출: 실제 상태를 무시한 '환각' 답변 (Validator 가 잡아낼 대상)
        return "고객님의 상품은 이미 배송이 완료되어 도착했습니다. "
                + "이미 도착한 상품은 취소가 아닌 반품 절차로만 처리 가능합니다.";
    }
}

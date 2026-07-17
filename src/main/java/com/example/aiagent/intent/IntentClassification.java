package com.example.aiagent.intent;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * Intent 분류 결과 (LLM 구조화 출력 스키마).
 *
 * <p>이 record 의 구조가 그대로 JSON 스키마로 변환되어 Claude 에 전달된다.
 * 따라서 Claude 는 반드시 이 형태로만 응답하며, 파싱 실패가 발생하지 않는다.</p>
 *
 * <p>{@code @JsonPropertyDescription} 은 스키마의 필드 설명으로 들어가 모델이
 * 각 필드의 의미를 정확히 이해하도록 돕는다. 즉, 이 주석들이 곧 프롬프트다.</p>
 *
 * @param intents       감지된 모든 의도 (복합 질의면 여러 개)
 * @param primaryIntent 가장 핵심이 되는 의도 (Router 가 Workflow 선택에 사용)
 * @param confidence    분류 신뢰도 0.0 ~ 1.0
 * @param reasoning     그렇게 분류한 이유 (관측/디버깅용)
 */
@JsonClassDescription("쇼핑몰 고객 문의의 의도 분류 결과. 하나의 질문에 여러 의도가 있을 수 있다.")
public record IntentClassification(

        @JsonPropertyDescription("""
                질문에 포함된 모든 의도를 빠짐없이 나열한다. 복합 질의는 반드시 여러 개를 반환한다.
                예: '지난주 주문한 상품이 안왔는데 취소하면 쿠폰도 돌려받나요?'
                    -> [ORDER_STATUS, SHIPPING, REFUND, COUPON]""")
        List<Intent> intents,

        @JsonPropertyDescription("""
                intents 중 사용자가 가장 알고 싶어 하는 핵심 의도 하나.
                반드시 intents 안에 포함된 값이어야 한다.
                위 예시에서는 최종적으로 '취소하면 쿠폰을 돌려받는지'를 묻고 있으므로 REFUND.""")
        Intent primaryIntent,

        @JsonPropertyDescription("분류 신뢰도. 0.0(전혀 확신 없음) ~ 1.0(매우 확신).")
        double confidence,

        @JsonPropertyDescription("이렇게 분류한 이유를 한국어 한두 문장으로 설명한다.")
        String reasoning
) {

    /** 복합 질의(의도가 2개 이상)인지 여부. */
    public boolean isComposite() {
        return intents != null && intents.size() > 1;
    }

    /** 분류 실패 시 사용할 안전한 기본값. */
    public static IntentClassification unknown(String reason) {
        return new IntentClassification(
                List.of(Intent.UNKNOWN),
                Intent.UNKNOWN,
                0.0,
                reason
        );
    }
}

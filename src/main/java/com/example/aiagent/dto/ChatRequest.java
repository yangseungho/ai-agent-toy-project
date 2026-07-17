package com.example.aiagent.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * REST 요청 Body.
 *
 * <pre>
 * {
 *   "conversationId": "conv-1",
 *   "customerId": "CUST-1",
 *   "question": "지난주 주문한 상품이 아직 안왔는데 취소하면 쿠폰도 돌려받을 수 있나요?"
 * }
 * </pre>
 *
 * @param conversationId 대화 세션 ID. 없으면 서버가 새로 발급한다(=새 대화).
 * @param customerId     고객 ID. 실전에서는 body 가 아니라 인증 토큰(JWT)에서 꺼내야 한다.
 *                       body 로 받으면 남의 주문을 조회할 수 있기 때문이다.
 * @param question       질문
 */
public record ChatRequest(
        String conversationId,
        String customerId,
        @NotBlank(message = "question 은 필수입니다.")
        String question
) {
}

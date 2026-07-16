package com.example.aiagent.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * REST 요청 Body.
 *
 * <pre>
 * {
 *   "question": "지난주 주문한 상품이 아직 안왔는데 취소하면 쿠폰도 돌려받을 수 있나요?"
 * }
 * </pre>
 */
@Getter
@Setter
@NoArgsConstructor
public class ChatRequest {

    /** 사용자의 질문 */
    private String question;
}

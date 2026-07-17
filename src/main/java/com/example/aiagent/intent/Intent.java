package com.example.aiagent.intent;

/**
 * 사용자 질문의 의도.
 *
 * <p>하나의 질문이 여러 의도를 동시에 가질 수 있다.
 * 예) "지난주 주문한 상품이 아직 안왔는데 취소하면 쿠폰도 돌려받나요?"
 * → {@code [ORDER_STATUS, SHIPPING, REFUND, COUPON]} (복합 질의)</p>
 */
public enum Intent {

    /** 주문 내역/상태 확인 */
    ORDER_STATUS,

    /** 배송 상태/도착 예정 문의 */
    SHIPPING,

    /** 환불/주문 취소 문의 */
    REFUND,

    /** 쿠폰 사용/복구 문의 */
    COUPON,

    /** 정책/약관 문의 (예: 환불 규정이 어떻게 되나요) */
    POLICY,

    /** 회원/계정 문의 */
    ACCOUNT,

    /** 위 어디에도 해당하지 않음 */
    UNKNOWN
}

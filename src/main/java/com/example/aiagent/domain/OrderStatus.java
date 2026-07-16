package com.example.aiagent.domain;

/**
 * 주문 상태.
 *
 * <p>교육용이므로 최소한의 상태만 정의한다.</p>
 */
public enum OrderStatus {

    /** 주문이 접수됨 */
    ORDERED,

    /** 주문이 취소됨 */
    CANCELLED,

    /** 배송이 완료됨 */
    DELIVERED
}

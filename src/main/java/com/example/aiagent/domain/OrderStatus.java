package com.example.aiagent.domain;

/** 주문 상태. */
public enum OrderStatus {
    /** 결제 완료, 배송 준비 중 */
    ORDERED,
    /** 취소됨 */
    CANCELLED,
    /** 배송 완료 */
    DELIVERED
}

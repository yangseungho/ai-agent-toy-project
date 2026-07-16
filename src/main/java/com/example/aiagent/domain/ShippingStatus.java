package com.example.aiagent.domain;

/**
 * 배송 상태.
 *
 * <p>Validator 가 "LLM 응답이 실제 배송 상태와 모순되는지"를 검사할 때 사용된다.</p>
 */
public enum ShippingStatus {

    /** 아직 배송이 시작되지 않음 (상품이 출발하지 않음) */
    NOT_SHIPPED,

    /** 배송 중 (배송기사 출발) */
    IN_TRANSIT,

    /** 배송 완료 (도착) */
    DELIVERED
}

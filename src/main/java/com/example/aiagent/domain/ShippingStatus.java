package com.example.aiagent.domain;

/**
 * 배송 상태.
 *
 * <p>외부 배송사 API 응답을 이 값으로 매핑한다. Validator 가 "LLM 답변이 실제 배송
 * 상태와 모순되는지" 검사할 때의 기준이 된다.</p>
 */
public enum ShippingStatus {
    /** 아직 출고 전 */
    NOT_SHIPPED,
    /** 배송 중 */
    IN_TRANSIT,
    /** 배송 완료 */
    DELIVERED,
    /** 외부 API 조회 실패 등으로 상태를 알 수 없음 */
    UNKNOWN
}

package com.example.aiagent.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

/**
 * 배송 도메인.
 *
 * <p>ShippingTool 이 반환하는 Mock 데이터의 원본 모델이다.</p>
 */
@Getter
@ToString
@AllArgsConstructor
public class Shipping {

    /** 배송 상태 */
    private final ShippingStatus status;

    /** 운송장 번호 */
    private final String trackingNumber;
}

package com.example.aiagent.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDate;

/**
 * 주문 도메인.
 *
 * <p>OrderTool 이 반환하는 Mock 데이터의 원본 모델이다.</p>
 */
@Getter
@ToString
@AllArgsConstructor
public class Order {

    /** 주문 식별자 */
    private final String orderId;

    /** 주문 상태 */
    private final OrderStatus status;

    /** 주문 일자 */
    private final LocalDate orderedAt;
}

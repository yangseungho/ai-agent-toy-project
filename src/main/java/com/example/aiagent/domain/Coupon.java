package com.example.aiagent.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

/**
 * 쿠폰 도메인.
 *
 * <p>CouponTool 이 반환하는 Mock 데이터의 원본 모델이다.</p>
 */
@Getter
@ToString
@AllArgsConstructor
public class Coupon {

    /** 쿠폰이 사용되었는지 여부 */
    private final boolean used;

    /** 주문 취소 시 쿠폰을 되돌려 받을 수 있는지 여부 */
    private final boolean recoverable;
}

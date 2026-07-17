package com.example.aiagent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 쿠폰 (PostgreSQL {@code coupons} 테이블).
 *
 * <p>주문에 사용된 쿠폰과, 주문 취소 시 복구 가능 여부를 담는다.</p>
 */
@Entity
@Table(name = "coupons")
@Getter
@ToString
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponEntity {

    @Id
    @Column(name = "coupon_id", nullable = false)
    private String couponId;

    /** 이 쿠폰이 사용된 주문 번호 */
    @Column(name = "order_id", nullable = false)
    private String orderId;

    /** 쿠폰명 */
    @Column(name = "name", nullable = false)
    private String name;

    /** 사용 여부 */
    @Column(name = "used", nullable = false)
    private boolean used;

    /** 주문 취소 시 복구 가능 여부 */
    @Column(name = "recoverable", nullable = false)
    private boolean recoverable;

    /** 할인 금액 */
    @Column(name = "discount_amount", nullable = false)
    private long discountAmount;
}

package com.example.aiagent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDate;

/**
 * 주문 (PostgreSQL {@code orders} 테이블).
 *
 * <p>OrderTool 이 이 엔티티를 JPA 로 조회한다. Mock 이 아니라 실제 DB 조회이다.</p>
 */
@Entity
@Table(name = "orders")
@Getter
@ToString
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA 요구사항
public class OrderEntity {

    /** 주문 번호 (예: ORD-1001) */
    @Id
    @Column(name = "order_id", nullable = false)
    private String orderId;

    /** 주문한 고객 ID */
    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    @Column(name = "ordered_at", nullable = false)
    private LocalDate orderedAt;

    /** 상품명 */
    @Column(name = "product_name", nullable = false)
    private String productName;

    /** 결제 금액 */
    @Column(name = "total_amount", nullable = false)
    private long totalAmount;
}

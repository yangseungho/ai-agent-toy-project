package com.example.aiagent.infra.persistence;

import com.example.aiagent.domain.CouponEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 쿠폰 조회 Repository (PostgreSQL).
 */
public interface CouponRepository extends JpaRepository<CouponEntity, String> {

    /** 특정 주문에 사용된 쿠폰 목록. */
    List<CouponEntity> findByOrderId(String orderId);
}

package com.example.aiagent.infra.persistence;

import com.example.aiagent.domain.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 주문 조회 Repository (PostgreSQL).
 */
public interface OrderRepository extends JpaRepository<OrderEntity, String> {

    /** 고객의 최근 주문을 정렬해서 조회한다. */
    List<OrderEntity> findByCustomerIdOrderByOrderedAtDesc(String customerId);

    /** 고객의 가장 최근 주문 1건. */
    Optional<OrderEntity> findFirstByCustomerIdOrderByOrderedAtDesc(String customerId);
}

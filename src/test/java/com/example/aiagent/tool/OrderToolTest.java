package com.example.aiagent.tool;

import com.example.aiagent.domain.OrderEntity;
import com.example.aiagent.domain.OrderStatus;
import com.example.aiagent.infra.persistence.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * OrderTool 단위 테스트 — DB(Repository)는 Mockito 로 mocking 한다.
 */
class OrderToolTest {

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final OrderTool orderTool = new OrderTool(orderRepository);

    private final ToolContext context = new StubToolContext("주문 확인해주세요", "CUST-1");

    @Test
    @DisplayName("주문이 있으면 주문 정보를 반환한다")
    void returnsOrderWhenFound() {
        OrderEntity order = new OrderEntity(
                "ORD-1001", "CUST-1", OrderStatus.ORDERED, LocalDate.of(2026, 7, 10), "무선 이어폰", 89000L);

        when(orderRepository.findFirstByCustomerIdOrderByOrderedAtDesc("CUST-1"))
                .thenReturn(Optional.of(order));

        ToolResult result = orderTool.execute(context);

        assertTrue(result.isSuccess());
        assertEquals("ORD-1001", result.get("orderId"));
        assertEquals("ORDERED", result.get("status"));
        assertTrue(result.getSummary().contains("무선 이어폰"));
    }

    @Test
    @DisplayName("주문이 없으면 '없음'을 사실로 반환한다 (실패가 아님)")
    void returnsEmptyWhenNoOrder() {
        when(orderRepository.findFirstByCustomerIdOrderByOrderedAtDesc(anyString()))
                .thenReturn(Optional.empty());

        ToolResult result = orderTool.execute(context);

        // 데이터 없음은 성공이지만 데이터가 비어 있다.
        assertTrue(result.isSuccess());
        assertFalse(result.hasData());
        assertTrue(result.getSummary().contains("존재하지 않"));
    }

    @Test
    @DisplayName("DB 장애가 나도 예외를 던지지 않고 실패 결과로 감싼다")
    void wrapsDbFailure() {
        when(orderRepository.findFirstByCustomerIdOrderByOrderedAtDesc(anyString()))
                .thenThrow(new DataAccessResourceFailureException("connection refused"));

        ToolResult result = orderTool.execute(context);

        // Tool 하나의 실패가 대화 전체를 죽이면 안 된다.
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("주문 DB 조회 실패"));
    }
}

package com.example.aiagent.tool;

import com.example.aiagent.domain.OrderStatus;
import com.example.aiagent.tool.order.OrderTool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * OrderTool 단위 테스트.
 */
class OrderToolTest {

    private final OrderTool orderTool = new OrderTool();

    @Test
    @DisplayName("OrderTool 은 주문 Mock 데이터를 반환한다")
    void execute() {
        ToolResult result = orderTool.execute("취소 문의");

        assertEquals(ToolNames.ORDER, result.getToolName());
        assertEquals(OrderStatus.ORDERED.name(), result.get("status"));
        assertEquals("ORD-1001", result.get("orderId"));
        assertNotNull(result.get("orderedAt"));
    }
}

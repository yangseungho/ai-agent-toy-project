package com.example.aiagent.tool.order;

import com.example.aiagent.domain.Order;
import com.example.aiagent.domain.OrderStatus;
import com.example.aiagent.tool.Tool;
import com.example.aiagent.tool.ToolNames;
import com.example.aiagent.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 주문 정보를 조회하는 Tool.
 *
 * <p>실제로는 주문 서비스/DB 를 조회하겠지만, 여기서는 고정된 Mock 주문을 반환한다.
 * 시나리오: "지난주에 주문했고 아직 배송되지 않은" 주문.</p>
 */
@Component
public class OrderTool implements Tool {

    @Override
    public String name() {
        return ToolNames.ORDER;
    }

    @Override
    public ToolResult execute(String question) {
        // Mock 주문: 7일 전에 주문했고 아직 주문 상태(ORDERED)
        Order order = new Order("ORD-1001", OrderStatus.ORDERED, LocalDate.now().minusDays(7));

        Map<String, String> data = new LinkedHashMap<>();
        data.put("orderId", order.getOrderId());
        data.put("status", order.getStatus().name());
        data.put("orderedAt", order.getOrderedAt().toString());

        String summary = "주문 " + order.getOrderId()
                + " 는 " + order.getOrderedAt() + " 에 접수되었으며 현재 상태는 "
                + order.getStatus() + " 입니다.";

        return new ToolResult(name(), summary, data);
    }
}

package com.example.aiagent.tool;

import com.example.aiagent.domain.ShippingStatus;
import com.example.aiagent.tool.shipping.ShippingTool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ShippingTool 단위 테스트.
 */
class ShippingToolTest {

    private final ShippingTool shippingTool = new ShippingTool();

    @Test
    @DisplayName("ShippingTool 은 NOT_SHIPPED 배송 Mock 데이터를 반환한다")
    void execute() {
        ToolResult result = shippingTool.execute("배송 문의");

        assertEquals(ToolNames.SHIPPING, result.getToolName());
        assertEquals(ShippingStatus.NOT_SHIPPED.name(), result.get("status"));
        assertEquals("TRK-0000-0000", result.get("trackingNumber"));
    }
}

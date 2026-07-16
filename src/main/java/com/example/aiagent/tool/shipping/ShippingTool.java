package com.example.aiagent.tool.shipping;

import com.example.aiagent.domain.Shipping;
import com.example.aiagent.domain.ShippingStatus;
import com.example.aiagent.tool.Tool;
import com.example.aiagent.tool.ToolNames;
import com.example.aiagent.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 배송 정보를 조회하는 Tool.
 *
 * <p>Mock 시나리오: 상품이 아직 출발하지 않은 상태(NOT_SHIPPED).
 * 이 값이 뒤에서 Validator 의 핵심 판단 근거가 된다. 즉, LLM 이 "배송이 완료되었다"
 * 같은 모순된 답변을 하면 Validator 가 이를 잡아낸다.</p>
 */
@Component
public class ShippingTool implements Tool {

    @Override
    public String name() {
        return ToolNames.SHIPPING;
    }

    @Override
    public ToolResult execute(String question) {
        // Mock 배송: 아직 출발하지 않음
        Shipping shipping = new Shipping(ShippingStatus.NOT_SHIPPED, "TRK-0000-0000");

        Map<String, String> data = new LinkedHashMap<>();
        data.put("status", shipping.getStatus().name());
        data.put("trackingNumber", shipping.getTrackingNumber());

        String summary = "배송 상태는 " + shipping.getStatus()
                + " (운송장 " + shipping.getTrackingNumber() + ") 입니다.";

        return new ToolResult(name(), summary, data);
    }
}

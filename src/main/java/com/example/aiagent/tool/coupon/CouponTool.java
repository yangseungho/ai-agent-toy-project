package com.example.aiagent.tool.coupon;

import com.example.aiagent.domain.Coupon;
import com.example.aiagent.tool.Tool;
import com.example.aiagent.tool.ToolNames;
import com.example.aiagent.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 쿠폰 정보를 조회하는 Tool.
 *
 * <p>Mock 시나리오: 주문에 쿠폰이 사용되었고, 취소 시 복구가 가능하다.</p>
 */
@Component
public class CouponTool implements Tool {

    @Override
    public String name() {
        return ToolNames.COUPON;
    }

    @Override
    public ToolResult execute(String question) {
        // Mock 쿠폰: 사용됨 + 복구 가능
        Coupon coupon = new Coupon(true, true);

        Map<String, String> data = new LinkedHashMap<>();
        data.put("used", String.valueOf(coupon.isUsed()));
        data.put("recoverable", String.valueOf(coupon.isRecoverable()));

        String summary = "쿠폰은 " + (coupon.isUsed() ? "사용됨" : "미사용")
                + " 상태이며, 주문 취소 시 "
                + (coupon.isRecoverable() ? "복구 가능합니다." : "복구 불가합니다.");

        return new ToolResult(name(), summary, data);
    }
}

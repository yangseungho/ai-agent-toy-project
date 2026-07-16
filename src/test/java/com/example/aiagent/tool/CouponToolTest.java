package com.example.aiagent.tool;

import com.example.aiagent.tool.coupon.CouponTool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * CouponTool 단위 테스트.
 */
class CouponToolTest {

    private final CouponTool couponTool = new CouponTool();

    @Test
    @DisplayName("CouponTool 은 사용됨/복구가능 쿠폰 Mock 데이터를 반환한다")
    void execute() {
        ToolResult result = couponTool.execute("쿠폰 문의");

        assertEquals(ToolNames.COUPON, result.getToolName());
        assertEquals("true", result.get("used"));
        assertEquals("true", result.get("recoverable"));
    }
}

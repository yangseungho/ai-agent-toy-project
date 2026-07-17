package com.example.aiagent.tool;

import com.example.aiagent.domain.CouponEntity;
import com.example.aiagent.infra.persistence.CouponRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CouponTool 단위 테스트 — DB 는 Mockito 로 mocking 한다.
 */
class CouponToolTest {

    private final CouponRepository couponRepository = mock(CouponRepository.class);
    private final CouponTool couponTool = new CouponTool(couponRepository);

    private ToolContext contextWithOrder() {
        return new StubToolContext("쿠폰 돌려받나요", "CUST-1")
                .withResult(ToolResult.success(ToolNames.ORDER, "주문 있음",
                        Map.of("orderId", "ORD-1001")));
    }

    @Test
    @DisplayName("주문에 사용된 쿠폰과 복구 가능 여부를 반환한다")
    void returnsCoupon() {
        when(couponRepository.findByOrderId("ORD-1001")).thenReturn(List.of(
                new CouponEntity("CPN-500", "ORD-1001", "10% 신규가입 쿠폰", true, true, 9000L)));

        ToolResult result = couponTool.execute(contextWithOrder());

        assertTrue(result.isSuccess());
        assertEquals("true", result.get("used"));
        assertEquals("true", result.get("recoverable"));
        assertTrue(result.getSummary().contains("복구 가능"));
    }

    @Test
    @DisplayName("사용된 쿠폰이 없으면 '없음'을 반환한다")
    void returnsEmptyWhenNoCoupon() {
        when(couponRepository.findByOrderId(anyString())).thenReturn(List.of());

        ToolResult result = couponTool.execute(contextWithOrder());

        assertTrue(result.isSuccess());
        assertFalse(result.hasData());
    }

    @Test
    @DisplayName("주문 정보가 없으면 DB 를 조회하지 않는다")
    void doesNotQueryWithoutOrder() {
        ToolResult result = couponTool.execute(new StubToolContext("쿠폰?", "CUST-1"));

        assertFalse(result.isSuccess());
        verify(couponRepository, never()).findByOrderId(anyString());
    }
}

package com.example.aiagent.tool;

import com.example.aiagent.infra.shipping.ShippingApiClient;
import com.example.aiagent.infra.shipping.ShippingApiException;
import com.example.aiagent.infra.shipping.ShippingApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ShippingTool 단위 테스트 — 외부 배송사 API 는 Mockito 로 mocking 한다.
 *
 * <p>실제 HTTP 호출은 하지 않는다. 외부 API 가 정상/없음/장애일 때 각각
 * 우리 Tool 이 올바른 ToolResult 를 만드는지 검증한다.</p>
 */
class ShippingToolTest {

    private final ShippingApiClient apiClient = mock(ShippingApiClient.class);
    private final ShippingTool shippingTool = new ShippingTool(apiClient);

    /** OrderTool 이 이미 주문을 찾아낸 상황 */
    private ToolContext contextWithOrder() {
        return new StubToolContext("배송 언제 오나요", "CUST-1")
                .withResult(ToolResult.success(ToolNames.ORDER, "주문 있음",
                        Map.of("orderId", "ORD-1001")));
    }

    @Test
    @DisplayName("외부 API 가 배송 정보를 주면 상태를 매핑해 반환한다")
    void mapsShippingStatus() {
        when(apiClient.findByOrderId("ORD-1001")).thenReturn(Optional.of(
                new ShippingApiResponse("ORD-1001", "IN_TRANSIT", "TRK-99", "한진택배", "2026-07-20")));

        ToolResult result = shippingTool.execute(contextWithOrder());

        assertTrue(result.isSuccess());
        assertEquals("IN_TRANSIT", result.get("status"));
        assertEquals("TRK-99", result.get("trackingNumber"));
    }

    @Test
    @DisplayName("외부 API 가 모르는 상태값을 주면 UNKNOWN 으로 매핑한다")
    void mapsUnknownStatusSafely() {
        when(apiClient.findByOrderId(anyString())).thenReturn(Optional.of(
                new ShippingApiResponse("ORD-1001", "WEIRD_NEW_STATUS", "TRK-99", "한진택배", null)));

        ToolResult result = shippingTool.execute(contextWithOrder());

        // 외부 스펙이 바뀌어도 터지지 않아야 한다.
        assertEquals("UNKNOWN", result.get("status"));
    }

    @Test
    @DisplayName("배송 정보가 아직 없으면(404) '출고 전'을 사실로 반환한다")
    void returnsEmptyWhenNoShipmentYet() {
        when(apiClient.findByOrderId(anyString())).thenReturn(Optional.empty());

        ToolResult result = shippingTool.execute(contextWithOrder());

        assertTrue(result.isSuccess());
        assertFalse(result.hasData());
        assertTrue(result.getSummary().contains("출고 전"));
    }

    @Test
    @DisplayName("외부 API 타임아웃 시 예외를 던지지 않고 실패 결과로 감싼다")
    void wrapsApiTimeout() {
        when(apiClient.findByOrderId(anyString()))
                .thenThrow(new ShippingApiException("배송사 API 연결 실패(타임아웃)", null));

        ToolResult result = shippingTool.execute(contextWithOrder());

        assertFalse(result.isSuccess());
        assertTrue(result.getSummary().contains("조회에 실패"));
    }

    @Test
    @DisplayName("주문 정보가 없으면 외부 API 를 호출조차 하지 않는다")
    void doesNotCallApiWithoutOrder() {
        ToolContext noOrder = new StubToolContext("배송 언제 오나요", "CUST-1");

        ToolResult result = shippingTool.execute(noOrder);

        assertFalse(result.isSuccess());
        // 불필요한 외부 호출을 막아야 한다.
        org.mockito.Mockito.verify(apiClient, org.mockito.Mockito.never()).findByOrderId(anyString());
    }
}

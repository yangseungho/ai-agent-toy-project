package com.example.aiagent.tool;

import com.example.aiagent.domain.ShippingStatus;
import com.example.aiagent.infra.shipping.ShippingApiClient;
import com.example.aiagent.infra.shipping.ShippingApiException;
import com.example.aiagent.infra.shipping.ShippingApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 배송 조회 Tool — <b>외부 배송사 REST API 를 실제 호출</b>한다.
 *
 * <p>DB 조회 Tool(OrderTool/CouponTool)과 대비되는 예시다. 실전 Agent 는 사내 DB와
 * 외부 API 를 섞어 쓰며, 외부 API 는 실패할 수 있다는 점이 핵심 차이다.</p>
 *
 * <p>이 Tool 이 반환하는 배송 상태는 Validator 가 LLM 답변의 사실 여부를 검증할 때
 * 기준으로 삼는 가장 중요한 근거다.</p>
 */
@Slf4j
@Component
public class ShippingTool implements Tool {

    private final ShippingApiClient shippingApiClient;

    public ShippingTool(ShippingApiClient shippingApiClient) {
        this.shippingApiClient = shippingApiClient;
    }

    @Override
    public String name() {
        return ToolNames.SHIPPING;
    }

    @Override
    public String description() {
        return "주문번호로 외부 배송사 API를 호출해 배송 상태와 운송장 정보를 조회한다.";
    }

    @Override
    public ToolResult execute(ToolContext context) {
        // OrderTool 이 찾아낸 주문번호가 있어야 배송 조회가 가능하다.
        ToolResult orderResult = context.toolResult(ToolNames.ORDER);
        if (orderResult == null || orderResult.get("orderId") == null) {
            return ToolResult.failure(name(), "주문 정보가 없어 배송을 조회할 수 없습니다. (OrderTool 선행 필요)");
        }
        String orderId = orderResult.get("orderId");

        try {
            Optional<ShippingApiResponse> found = shippingApiClient.findByOrderId(orderId);

            if (found.isEmpty()) {
                // 404 = 아직 송장이 생성되지 않음. 이것도 '사실'이므로 정확히 전달한다.
                return ToolResult.empty(name(),
                        "주문 " + orderId + " 는 아직 배송 정보가 생성되지 않았습니다. (출고 전)");
            }

            ShippingApiResponse shipping = found.get();
            ShippingStatus status = parseStatus(shipping.status());

            Map<String, String> data = new LinkedHashMap<>();
            data.put("orderId", orderId);
            data.put("status", status.name());
            data.put("trackingNumber", nullSafe(shipping.trackingNumber()));
            data.put("carrier", nullSafe(shipping.carrier()));
            data.put("estimatedDelivery", nullSafe(shipping.estimatedDelivery()));

            String summary = String.format(
                    "주문 %s 의 배송 상태는 %s 입니다. (배송사: %s, 운송장: %s, 도착예정: %s)",
                    orderId, status, nullSafe(shipping.carrier()),
                    nullSafe(shipping.trackingNumber()), nullSafe(shipping.estimatedDelivery()));

            return ToolResult.success(name(), summary, data);

        } catch (ShippingApiException e) {
            // 외부 API 장애. 대화를 죽이지 않고 '확인 불가'라는 사실을 전달한다.
            // 이렇게 해야 모델이 "배송 완료됐습니다" 같은 추측을 하지 않는다.
            log.error("[ShippingTool] 외부 배송 API 실패 orderId={}", orderId, e);
            return ToolResult.failure(name(), e.getMessage());
        }
    }

    /** 외부 API 의 상태 문자열을 내부 enum 으로 매핑한다. 모르는 값은 UNKNOWN. */
    private ShippingStatus parseStatus(String rawStatus) {
        if (rawStatus == null) {
            return ShippingStatus.UNKNOWN;
        }
        try {
            return ShippingStatus.valueOf(rawStatus.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("[ShippingTool] 알 수 없는 배송 상태값: {}", rawStatus);
            return ShippingStatus.UNKNOWN;
        }
    }

    private String nullSafe(String value) {
        return value == null ? "정보없음" : value;
    }
}

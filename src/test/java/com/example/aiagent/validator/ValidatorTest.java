package com.example.aiagent.validator;

import com.example.aiagent.dto.AgentContext;
import com.example.aiagent.intent.Intent;
import com.example.aiagent.intent.IntentClassification;
import com.example.aiagent.tool.ToolNames;
import com.example.aiagent.tool.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validator 단위 테스트 — 환각 방지 가드레일이 실제로 막는지 검증한다.
 *
 * <p>Validator 는 외부 I/O 가 없으므로 mock 이 필요 없다.</p>
 */
class ValidatorTest {

    private final Validator validator = new Validator();

    private AgentContext context() {
        return new AgentContext("conv-1", "CUST-1", "배송 언제 오나요?",
                new IntentClassification(List.of(Intent.SHIPPING), Intent.SHIPPING, 0.9, "배송 문의"),
                List.of());
    }

    private AgentContext contextWithShippingStatus(String status) {
        AgentContext context = context();
        context.addToolResult(ToolResult.success(ToolNames.SHIPPING, "배송 정보",
                Map.of("status", status, "orderId", "ORD-1001")));
        return context;
    }

    @Test
    @DisplayName("출고 전인데 '배송 완료'라고 하면 검증 실패")
    void failsWhenClaimingDeliveredButNotShipped() {
        ValidationResult result = validator.validate(
                contextWithShippingStatus("NOT_SHIPPED"),
                "고객님의 상품은 이미 배송이 완료되어 도착했습니다.");

        assertFalse(result.isValid());
        assertTrue(result.getReason().contains("NOT_SHIPPED"));
    }

    @Test
    @DisplayName("출고 전이고 '아직 출발 전'이라고 하면 검증 통과")
    void passesWhenConsistent() {
        ValidationResult result = validator.validate(
                contextWithShippingStatus("NOT_SHIPPED"),
                "고객님의 상품은 아직 배송이 시작되지 않았습니다. 지금은 취소가 가능합니다.");

        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("배송 완료인데 '아직 출발 전'이라고 하면 검증 실패")
    void failsWhenClaimingNotShippedButDelivered() {
        ValidationResult result = validator.validate(
                contextWithShippingStatus("DELIVERED"),
                "아직 배송이 시작되지 않았습니다.");

        assertFalse(result.isValid());
    }

    @Test
    @DisplayName("배송 조회에 실패했는데 상태를 단정하면 검증 실패")
    void failsWhenAssertingStatusAfterApiFailure() {
        AgentContext context = context();
        context.addToolResult(ToolResult.failure(ToolNames.SHIPPING, "배송사 API 타임아웃"));

        ValidationResult result = validator.validate(context, "상품은 현재 배송 중입니다.");

        // 조회도 못 했는데 단정하면 그건 지어낸 것이다.
        assertFalse(result.isValid());
        assertTrue(result.getReason().contains("조회에 실패"));
    }

    @Test
    @DisplayName("배송 조회 실패 시 '확인 어렵다'고 하면 검증 통과")
    void passesWhenAdmittingUncertainty() {
        AgentContext context = context();
        context.addToolResult(ToolResult.failure(ToolNames.SHIPPING, "배송사 API 타임아웃"));

        ValidationResult result = validator.validate(context,
                "현재 배송 정보를 확인하기 어렵습니다. 잠시 후 다시 시도해 주세요.");

        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("실제와 다른 주문번호를 지어내면 검증 실패")
    void failsOnFabricatedOrderId() {
        AgentContext context = context();
        context.addToolResult(ToolResult.success(ToolNames.ORDER, "주문",
                Map.of("orderId", "ORD-1001", "status", "ORDERED")));

        ValidationResult result = validator.validate(context, "주문번호 ORD-9999 는 정상 접수되었습니다.");

        assertFalse(result.isValid());
        assertTrue(result.getReason().contains("ORD-1001"));
    }

    @Test
    @DisplayName("실제 주문번호를 정확히 언급하면 검증 통과")
    void passesOnCorrectOrderId() {
        AgentContext context = context();
        context.addToolResult(ToolResult.success(ToolNames.ORDER, "주문",
                Map.of("orderId", "ORD-1001", "status", "ORDERED")));

        ValidationResult result = validator.validate(context, "주문번호 ORD-1001 은 정상 접수되었습니다.");

        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("빈 응답은 검증 실패")
    void failsOnEmptyAnswer() {
        assertFalse(validator.validate(context(), "").isValid());
    }

    @Test
    @DisplayName("배송을 조회하지 않은 흐름은 배송 검사를 건너뛴다")
    void skipsShippingCheckWhenNotQueried() {
        ValidationResult result = validator.validate(context(), "환불 정책은 다음과 같습니다.");

        assertTrue(result.isValid());
    }
}

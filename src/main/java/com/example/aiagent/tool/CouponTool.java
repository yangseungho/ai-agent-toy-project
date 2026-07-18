package com.example.aiagent.tool;

import com.example.aiagent.domain.CouponEntity;
import com.example.aiagent.infra.persistence.CouponRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 쿠폰 조회 Tool — <b>PostgreSQL 을 JPA 로 실제 조회</b>한다.
 *
 * <p>OrderTool 이 찾아낸 orderId 로 해당 주문에 사용된 쿠폰을 조회한다.
 * (Tool 간 데이터 의존성 — Agent Loop 가 순서를 보장한다.)</p>
 */
@Slf4j
@Component
public class CouponTool implements Tool {

    private final CouponRepository couponRepository;

    public CouponTool(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    @Override
    public String name() {
        return ToolNames.COUPON;
    }

    @Override
    public String description() {
        return "특정 주문에 사용된 쿠폰과 취소 시 복구 가능 여부를 DB에서 조회한다.";
    }

    /** 쿠폰 조회에도 주문번호가 필요하다. ShippingTool 과 의존성이 같아 서로 병렬 실행된다. */
    @Override
    public Set<String> requiredInputs() {
        return Set.of("orderId");
    }

    @Override
    public ToolResult execute(ToolContext context) {
        String orderId = context.input("orderId");
        if (orderId == null) {
            return ToolResult.failure(name(), "주문 정보가 없어 쿠폰을 조회할 수 없습니다. (orderId 미확보)");
        }

        try {
            List<CouponEntity> coupons = couponRepository.findByOrderId(orderId);

            if (coupons.isEmpty()) {
                return ToolResult.empty(name(),
                        "주문 " + orderId + " 에는 사용된 쿠폰이 없습니다.");
            }

            // 시나리오 단순화를 위해 첫 번째 쿠폰만 사용한다.
            CouponEntity coupon = coupons.get(0);

            Map<String, String> data = new LinkedHashMap<>();
            data.put("couponId", coupon.getCouponId());
            data.put("name", coupon.getName());
            data.put("used", String.valueOf(coupon.isUsed()));
            data.put("recoverable", String.valueOf(coupon.isRecoverable()));
            data.put("discountAmount", String.valueOf(coupon.getDiscountAmount()));

            String summary = String.format(
                    "주문 %s 에는 '%s'(%,d원 할인) 쿠폰이 %s 상태이며, 주문 취소 시 %s.",
                    orderId, coupon.getName(), coupon.getDiscountAmount(),
                    coupon.isUsed() ? "사용됨" : "미사용",
                    coupon.isRecoverable() ? "복구 가능합니다" : "복구되지 않습니다");

            return ToolResult.success(name(), summary, data);

        } catch (DataAccessException e) {
            log.error("[CouponTool] DB 조회 실패 orderId={}", orderId, e);
            return ToolResult.failure(name(), "쿠폰 DB 조회 실패: " + e.getMessage());
        }
    }
}

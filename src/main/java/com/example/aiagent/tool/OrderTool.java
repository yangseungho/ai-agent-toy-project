package com.example.aiagent.tool;

import com.example.aiagent.domain.OrderEntity;
import com.example.aiagent.infra.persistence.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 주문 조회 Tool — <b>PostgreSQL 을 JPA 로 실제 조회</b>한다.
 *
 * <p>고객의 가장 최근 주문을 찾는다. 실전이라면 질문에서 주문번호를 추출하거나
 * 주문 목록을 보여주고 고르게 하겠지만, 여기서는 흐름에 집중하기 위해 최근 주문 1건을 쓴다.</p>
 *
 * <p>이 Tool 이 찾아낸 {@code orderId} 는 ShippingTool/CouponTool 이 사용한다.
 * 그래서 Planner 가 이 Tool 을 항상 먼저 호출한다.</p>
 */
@Slf4j
@Component
public class OrderTool implements Tool {

    private final OrderRepository orderRepository;

    public OrderTool(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public String name() {
        return ToolNames.ORDER;
    }

    @Override
    public String description() {
        return "고객의 최근 주문 정보(주문번호, 주문상태, 주문일, 상품명, 결제금액)를 DB에서 조회한다.";
    }

    @Override
    public ToolResult execute(ToolContext context) {
        try {
            Optional<OrderEntity> found =
                    orderRepository.findFirstByCustomerIdOrderByOrderedAtDesc(context.customerId());

            if (found.isEmpty()) {
                // 데이터가 없는 것은 실패가 아니다. '없음'을 사실로 알려 모델이 지어내지 않게 한다.
                return ToolResult.empty(name(),
                        "고객(" + context.customerId() + ")의 주문 내역이 존재하지 않습니다.");
            }

            OrderEntity order = found.get();

            Map<String, String> data = new LinkedHashMap<>();
            data.put("orderId", order.getOrderId());
            data.put("status", order.getStatus().name());
            data.put("orderedAt", order.getOrderedAt().toString());
            data.put("productName", order.getProductName());
            data.put("totalAmount", String.valueOf(order.getTotalAmount()));

            String summary = String.format(
                    "주문번호 %s: '%s' 상품을 %s에 %,d원에 주문했으며 현재 주문상태는 %s 입니다.",
                    order.getOrderId(), order.getProductName(), order.getOrderedAt(),
                    order.getTotalAmount(), order.getStatus());

            return ToolResult.success(name(), summary, data);

        } catch (DataAccessException e) {
            // DB 장애가 나도 대화 전체를 죽이지 않는다. '조회 실패'를 사실로 전달한다.
            log.error("[OrderTool] DB 조회 실패 customerId={}", context.customerId(), e);
            return ToolResult.failure(name(), "주문 DB 조회 실패: " + e.getMessage());
        }
    }
}

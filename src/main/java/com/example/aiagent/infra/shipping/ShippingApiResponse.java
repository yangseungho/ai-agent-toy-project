package com.example.aiagent.infra.shipping;

/**
 * 외부 배송사 API 응답 DTO.
 *
 * <p>실제 배송사 API 의 JSON 스펙에 맞춰 필드를 정의한다.
 * 예상 응답:</p>
 * <pre>
 * GET /shipments?orderId=ORD-1001
 * {
 *   "orderId": "ORD-1001",
 *   "status": "NOT_SHIPPED",
 *   "trackingNumber": "TRK-0000-0000",
 *   "carrier": "한진택배",
 *   "estimatedDelivery": "2026-07-20"
 * }
 * </pre>
 *
 * @param orderId           주문번호
 * @param status            배송 상태 문자열 (NOT_SHIPPED / IN_TRANSIT / DELIVERED)
 * @param trackingNumber    운송장 번호 (출고 전이면 null 일 수 있음)
 * @param carrier           배송사명
 * @param estimatedDelivery 도착 예정일
 */
public record ShippingApiResponse(
        String orderId,
        String status,
        String trackingNumber,
        String carrier,
        String estimatedDelivery
) {
}

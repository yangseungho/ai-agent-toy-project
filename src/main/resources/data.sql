-- ============================================================================
--  데모 데이터
--
--  실제 서비스라면 주문/쿠폰은 각 도메인 서비스가 적재한다.
--  이 프로젝트는 Agent 동작을 보기 위한 최소 데이터만 넣는다.
--  (Mock 객체가 아니라 실제 PostgreSQL 에 들어가는 진짜 행이다.)
--
--  시나리오: 고객 CUST-1 이 지난주에 주문했고 아직 배송 출발 전이며 쿠폰을 사용했다.
-- ============================================================================

INSERT INTO orders (order_id, customer_id, status, ordered_at, product_name, total_amount)
VALUES ('ORD-1001', 'CUST-1', 'ORDERED', CURRENT_DATE - 7, '무선 이어폰', 89000)
ON CONFLICT (order_id) DO NOTHING;

INSERT INTO orders (order_id, customer_id, status, ordered_at, product_name, total_amount)
VALUES ('ORD-1000', 'CUST-1', 'DELIVERED', CURRENT_DATE - 30, '키보드', 45000)
ON CONFLICT (order_id) DO NOTHING;

INSERT INTO coupons (coupon_id, order_id, name, used, recoverable, discount_amount)
VALUES ('CPN-500', 'ORD-1001', '10% 신규가입 쿠폰', true, true, 9000)
ON CONFLICT (coupon_id) DO NOTHING;

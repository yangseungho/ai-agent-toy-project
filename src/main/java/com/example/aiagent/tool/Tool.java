package com.example.aiagent.tool;

/**
 * Agent 가 호출할 수 있는 Tool.
 *
 * <p>각 구현체는 실제 외부 시스템을 호출한다.</p>
 * <ul>
 *     <li>{@code OrderTool}     → PostgreSQL (JPA)</li>
 *     <li>{@code CouponTool}    → PostgreSQL (JPA)</li>
 *     <li>{@code ShippingTool}  → 외부 배송사 REST API (RestClient)</li>
 *     <li>{@code PolicyRagTool} → pgvector Vector DB (유사도 검색)</li>
 * </ul>
 */
public interface Tool {

    /** Planner 가 이 이름으로 Tool 을 지목한다. */
    String name();

    /** 이 Tool 이 무엇을 조회하는지 (프롬프트/로그용 설명). */
    String description();

    /**
     * Tool 을 실행한다.
     *
     * <p>구현체는 외부 I/O 예외를 밖으로 던지지 말고 {@link ToolResult#failure}
     * 로 감싸 반환해야 한다. 하나의 Tool 실패가 전체 대화를 실패시키면 안 되기 때문이다.</p>
     */
    ToolResult execute(ToolContext context);
}

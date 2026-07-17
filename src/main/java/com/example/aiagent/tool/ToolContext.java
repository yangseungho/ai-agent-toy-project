package com.example.aiagent.tool;

/**
 * Tool 이 실행 시 참조할 수 있는 <b>읽기 전용</b> 컨텍스트.
 *
 * <p>Tool 은 Agent 의 전체 상태를 알 필요가 없다. 필요한 것만 이 인터페이스로 노출한다.
 * (덕분에 tool 패키지는 상위 계층에 의존하지 않는다.)</p>
 *
 * <p>Tool 이 이전 Tool 의 결과를 참조해야 하는 경우가 있다.
 * 예) ShippingTool/CouponTool 은 OrderTool 이 찾아낸 orderId 가 있어야 조회할 수 있다.
 * 이것이 Agent Loop 가 필요한 이유이기도 하다 — 순서와 의존성이 있는 조회.</p>
 */
public interface ToolContext {

    /** 이번 턴 사용자 질문 */
    String question();

    /** 요청 고객 ID */
    String customerId();

    /**
     * 이미 실행된 Tool 의 결과를 조회한다.
     *
     * @return 아직 실행되지 않았으면 null
     */
    ToolResult toolResult(String toolName);
}

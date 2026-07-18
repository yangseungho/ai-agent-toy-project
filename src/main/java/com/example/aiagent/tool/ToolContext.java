package com.example.aiagent.tool;

import java.util.Set;

/**
 * Tool 이 실행 시 참조할 수 있는 <b>읽기 전용</b> 컨텍스트.
 *
 * <p>Tool 은 Agent 의 전체 상태를 알 필요가 없다. 필요한 것만 이 인터페이스로 노출한다.
 * (덕분에 tool 패키지는 상위 계층에 의존하지 않는다.)</p>
 *
 * <p><b>블랙보드 모델.</b> Tool 이 앞선 Tool 의 결과를 참조할 때 "OrderTool 의 결과에서
 * orderId 를 꺼낸다"가 아니라 {@link #input(String) input("orderId")} 로 <b>값만</b>
 * 요청한다. 누가 그 값을 만들었는지는 알 필요가 없다 — 덕분에 나중에 orderId 를
 * 다른 Tool(예: MCP 로 붙인 주문 서비스)이 만들어도 ShippingTool 은 그대로 동작한다.</p>
 *
 * <p>동시 실행되는 Tool 들이 같은 인스턴스를 공유하므로 이 인터페이스의 구현은
 * <b>읽기에 대해 스레드 안전</b>해야 한다.</p>
 */
public interface ToolContext {

    /** 이번 턴 사용자 질문 */
    String question();

    /** 요청 고객 ID */
    String customerId();

    /**
     * 지금까지 수집된 값 중 하나를 조회한다 (블랙보드 조회).
     *
     * <p>먼저 실행된 Tool 의 결과부터 찾으며, 같은 키를 여러 Tool 이 만들었다면
     * <b>가장 먼저 만든 값</b>을 돌려준다.</p>
     *
     * @return 아직 아무도 그 값을 만들지 않았으면 null
     */
    String input(String key);

    /** 현재 조회 가능한 모든 입력 키. Planner 가 Tool 실행 준비 여부를 판단할 때 쓴다. */
    Set<String> availableInputs();

    /**
     * 이미 실행된 Tool 의 결과를 통째로 조회한다.
     *
     * @return 아직 실행되지 않았으면 null
     */
    ToolResult toolResult(String toolName);
}

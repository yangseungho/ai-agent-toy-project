package com.example.aiagent.tool;

import java.util.Set;

/**
 * Agent 가 호출할 수 있는 Tool.
 *
 * <p>구현체는 크게 두 종류다.</p>
 * <ul>
 *     <li><b>로컬 Tool</b> — 우리 서비스가 직접 소유한 자원을 조회한다.
 *         {@code OrderTool}/{@code CouponTool}(PostgreSQL), {@code PolicyRagTool}(pgvector)</li>
 *     <li><b>MCP Tool</b> — 외부 MCP 서버가 노출한 Tool 을 원격 호출한다.
 *         ({@code McpToolAdapter}) 기동 시 {@code tools/list} 로 <b>동적 발견</b>되므로
 *         코드를 고치지 않고도 Tool 이 늘어난다.</li>
 * </ul>
 *
 * <p>Agent 코어는 이 둘을 구분하지 않는다. Planner 는 {@link #requiredInputs()} 만 보고
 * 실행 순서와 병렬 가능 여부를 계산한다.</p>
 */
public interface Tool {

    /** Planner 가 이 이름으로 Tool 을 지목한다. */
    String name();

    /** 이 Tool 이 무엇을 조회하는지 (프롬프트/로그용 설명). */
    String description();

    /**
     * 이 Tool 이 실행되기 전에 컨텍스트에 존재해야 하는 입력 키.
     *
     * <p>Planner 는 이 값만으로 의존성 그래프를 만든다. 예를 들어 ShippingTool 이
     * {@code {"orderId"}} 를 선언하면, orderId 를 만들어내는 Tool(OrderTool)이 먼저
     * 실행되어야 한다는 사실이 자동으로 도출된다.</p>
     *
     * <p>이렇게 <b>Tool 이 스스로 의존성을 선언</b>하게 하는 이유는, 예전처럼 Planner 안에
     * 고정된 실행 순서 목록을 두면 Tool 이 늘어날 때마다 Planner 를 고쳐야 하기 때문이다.
     * 특히 MCP Tool 은 런타임에 발견되므로 애초에 하드코딩이 불가능하다.</p>
     *
     * <p>비어 있으면 <b>아무것도 기다리지 않는다</b> = 다른 Tool 과 병렬 실행 가능하다.</p>
     */
    default Set<String> requiredInputs() {
        return Set.of();
    }

    /**
     * Tool 을 실행한다.
     *
     * <p>구현체는 외부 I/O 예외를 밖으로 던지지 말고 {@link ToolResult#failure}
     * 로 감싸 반환해야 한다. 하나의 Tool 실패가 전체 대화를 실패시키면 안 되기 때문이다.</p>
     *
     * <p><b>스레드 안전해야 한다.</b> 같은 wave 에 속한 Tool 들은 서로 다른 스레드에서
     * 동시에 실행된다. 넘겨받은 {@link ToolContext} 는 읽기 전용이므로, 구현체가
     * 가변 상태를 필드에 들고 있지만 않으면 자연히 안전하다.</p>
     */
    ToolResult execute(ToolContext context);
}

package com.example.aiagent.infra.mcp;

import com.example.aiagent.tool.Tool;
import com.example.aiagent.tool.ToolContext;
import com.example.aiagent.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 원격 MCP Tool 을 우리 {@link Tool} 인터페이스로 감싼다.
 *
 * <p>이 어댑터가 있어서 Agent 코어는 "이 Tool 이 로컬 DB 를 보는지 남의 서버를 부르는지"를
 * 전혀 몰라도 된다. Planner 도 ToolExecutor 도 Validator 도 코드가 그대로다.</p>
 *
 * <h2>인자 바인딩</h2>
 * <p>MCP 서버는 인자를 JSON Schema 로 요구한다. 그 값을 어디서 채울지가 문제인데,
 * 여기서는 컨텍스트(블랙보드)에서 <b>같은 이름의 값</b>을 찾아 넣는다.
 * 배송 Tool 이 {@code orderId} 를 요구하면 OrderTool 이 만들어 둔 orderId 가 들어가는 식이다.
 * {@code customerId}/{@code question} 처럼 턴 내내 존재하는 값은 항상 채워진다.</p>
 *
 * <p>이 규칙 덕분에 서버가 Tool 을 새로 추가해도, 그 Tool 이 요구하는 인자 이름이
 * 기존에 존재하는 값이기만 하면 코드 수정 없이 바로 쓸 수 있다.</p>
 */
@Slf4j
public class McpToolAdapter implements Tool {

    /** 스키마가 요구하더라도 컨텍스트에서 항상 채울 수 있는 값들. */
    private static final String CUSTOMER_ID = "customerId";
    private static final String QUESTION = "question";

    private final McpClient client;
    private final McpToolSpec spec;

    public McpToolAdapter(McpClient client, McpToolSpec spec) {
        this.client = client;
        this.spec = spec;
    }

    @Override
    public String name() {
        return spec.name();
    }

    @Override
    public String description() {
        return spec.description();
    }

    /**
     * 원격 Tool 의 의존성은 <b>스키마에서 도출</b>된다.
     *
     * <p>required 인자 중 우리가 항상 제공할 수 있는 것(customerId/question)을 빼면,
     * 남는 것은 "다른 Tool 이 먼저 만들어 줘야 하는 값"이다. 이것이 곧 실행 순서를 정한다.</p>
     */
    @Override
    public Set<String> requiredInputs() {
        Set<String> required = new java.util.LinkedHashSet<>(spec.requiredArguments());
        required.remove(CUSTOMER_ID);
        required.remove(QUESTION);
        return required;
    }

    @Override
    public ToolResult execute(ToolContext context) {
        Map<String, String> arguments = bindArguments(context);

        try {
            McpClient.McpCallResult result = client.callTool(spec.name(), arguments);

            if (result.isError()) {
                // Tool 은 정상 동작했지만 작업이 실패한 경우다. 이것도 '사실'이므로
                // 그대로 전달해야 모델이 지어내지 않는다.
                return ToolResult.failure(name(),
                        result.text().isBlank() ? "원격 Tool 실행 실패" : result.text());
            }

            if (result.text().isBlank() && result.structured().isEmpty()) {
                return ToolResult.empty(name(), "(" + name() + ") 조회 결과가 비어 있습니다.");
            }

            return ToolResult.success(name(), result.text(), result.structured());

        } catch (McpException e) {
            // MCP 서버 장애/타임아웃. 대화를 죽이지 않고 '확인 불가'로 넘긴다.
            log.error("[MCP:{}] Tool 호출 실패 tool={}", client.getServerName(), spec.name(), e);
            return ToolResult.failure(name(), e.getMessage());
        }
    }

    /** 스키마가 요구하는 인자를 컨텍스트에서 채운다. */
    private Map<String, String> bindArguments(ToolContext context) {
        Map<String, String> arguments = new LinkedHashMap<>();

        for (String argument : spec.requiredArguments()) {
            String value = switch (argument) {
                case CUSTOMER_ID -> context.customerId();
                case QUESTION -> context.question();
                default -> context.input(argument);
            };
            if (value != null) {
                arguments.put(argument, value);
            } else {
                // Planner 가 requiredInputs 로 막아주므로 정상 흐름에서는 오지 않는다.
                log.warn("[MCP:{}] 인자 {} 를 채우지 못했다 (tool={})",
                        client.getServerName(), argument, spec.name());
            }
        }
        return arguments;
    }
}

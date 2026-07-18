package com.example.aiagent.infra.mcp;

import com.example.aiagent.dto.AgentContext;
import com.example.aiagent.infra.persistence.OrderRepository;
import com.example.aiagent.intent.Intent;
import com.example.aiagent.intent.IntentClassification;
import com.example.aiagent.planner.PlanStep;
import com.example.aiagent.planner.Planner;
import com.example.aiagent.tool.OrderTool;
import com.example.aiagent.tool.StubToolContext;
import com.example.aiagent.tool.Tool;
import com.example.aiagent.tool.ToolNames;
import com.example.aiagent.tool.ToolRegistry;
import com.example.aiagent.tool.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * McpToolAdapter 테스트 — 원격 Tool 이 로컬 Tool 과 <b>구별 없이</b> 동작하는지 확인한다.
 */
class McpToolAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final McpClient client = mock(McpClient.class);

    /** JSON Schema 로부터 Tool 명세를 만든다. */
    private McpToolSpec spec(String name, String schemaJson) {
        try {
            return new McpToolSpec(name, name + " 설명", objectMapper.readTree(schemaJson));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private McpToolSpec shippingSpec() {
        return spec("ShippingTool", """
                {"type":"object","properties":{"orderId":{"type":"string"}},"required":["orderId"]}
                """);
    }

    @Test
    @DisplayName("스키마의 required 에서 의존성이 도출된다")
    void derivesDependenciesFromSchema() {
        Tool tool = new McpToolAdapter(client, shippingSpec());

        // 우리 코드에 "배송은 주문 다음"이라고 적어둔 곳이 없다.
        // 서버가 준 스키마만 보고 알아낸 것이다.
        assertEquals(Set.of("orderId"), tool.requiredInputs());
    }

    @Test
    @DisplayName("항상 제공 가능한 인자(customerId/question)는 의존성으로 치지 않는다")
    void treatsAlwaysAvailableArgumentsAsNonBlocking() {
        Tool tool = new McpToolAdapter(client, spec("SearchTool", """
                {"type":"object",
                 "properties":{"question":{"type":"string"},"customerId":{"type":"string"}},
                 "required":["question","customerId"]}
                """));

        // 이 값들은 턴 시작부터 존재하므로 아무도 기다릴 필요가 없다 → 첫 wave 에 병렬 실행된다.
        assertTrue(tool.requiredInputs().isEmpty());
    }

    @Test
    @DisplayName("컨텍스트(블랙보드)에서 인자를 채워 원격 호출한다")
    void bindsArgumentsFromContext() {
        when(client.callTool(anyString(), anyMap()))
                .thenReturn(new McpClient.McpCallResult(
                        "주문 ORD-1001 은 출고 전입니다.",
                        Map.of("status", "NOT_SHIPPED"), false));

        Tool tool = new McpToolAdapter(client, shippingSpec());

        // OrderTool 이 먼저 만들어 둔 orderId 가 그대로 원격 인자로 들어가야 한다.
        StubToolContext context = new StubToolContext("배송 어디쯤인가요", "CUST-1")
                .withResult(ToolResult.success(
                        ToolNames.ORDER, "주문 있음", Map.of("orderId", "ORD-1001")));

        ToolResult result = tool.execute(context);

        verify(client).callTool(eq("ShippingTool"), eq(Map.of("orderId", "ORD-1001")));
        assertTrue(result.isSuccess());
        assertEquals("NOT_SHIPPED", result.get("status"));
    }

    @Test
    @DisplayName("원격 Tool 실행 실패는 대화를 죽이지 않고 실패 결과로 전달된다")
    void convertsExecutionErrorToFailure() {
        when(client.callTool(anyString(), anyMap()))
                .thenReturn(new McpClient.McpCallResult("운송장을 찾을 수 없습니다.", Map.of(), true));

        ToolResult result = new McpToolAdapter(client, shippingSpec())
                .execute(new StubToolContext("q", "CUST-1")
                        .withResult(ToolResult.success(ToolNames.ORDER, "주문", Map.of("orderId", "X"))));

        assertFalse(result.isSuccess());
        assertTrue(result.getSummary().contains("찾을 수 없"));
    }

    @Test
    @DisplayName("MCP 서버 장애도 실패 결과로 감싸 전달된다")
    void convertsTransportFailureToFailure() {
        when(client.getServerName()).thenReturn("shipping");
        when(client.callTool(anyString(), anyMap()))
                .thenThrow(new McpException("MCP 서버(shipping) 호출 실패: timeout"));

        ToolResult result = new McpToolAdapter(client, shippingSpec())
                .execute(new StubToolContext("q", "CUST-1")
                        .withResult(ToolResult.success(ToolNames.ORDER, "주문", Map.of("orderId", "X"))));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("timeout"));
    }

    @Test
    @DisplayName("MCP Tool 은 같은 이름의 로컬 Tool 을 대체하고 Planner 는 그대로 동작한다")
    void mcpToolOverridesLocalToolTransparently() {
        // 하이브리드 구성: 주문은 로컬 DB Tool, 배송은 MCP 서버 Tool
        ToolRegistry registry = new ToolRegistry(List.of(
                new OrderTool(mock(OrderRepository.class)),
                new com.example.aiagent.tool.ShippingTool(
                        mock(com.example.aiagent.infra.shipping.ShippingApiClient.class))));

        registry.register(new McpToolAdapter(client, shippingSpec()));

        // 이름은 그대로지만 구현이 원격으로 바뀌었다.
        assertTrue(registry.get(ToolNames.SHIPPING) instanceof McpToolAdapter,
                "MCP Tool 이 로컬 폴백을 대체해야 한다");

        // Planner 는 이 교체를 전혀 모른다 — 의존성만 보고 똑같이 계획한다.
        Planner planner = new Planner(registry);
        AgentContext context = new AgentContext(
                "conv-1", "CUST-1", "배송 어디쯤인가요",
                new IntentClassification(List.of(Intent.SHIPPING), Intent.SHIPPING, 0.9, "테스트"),
                List.of());
        context.setPlan(planner.plan(context));

        List<String> firstWave = planner.nextBatch(context).stream()
                .map(PlanStep::getToolName).toList();
        assertEquals(List.of(ToolNames.ORDER), firstWave, "배송은 orderId 를 기다려야 한다");

        context.addToolResult(ToolResult.success(
                ToolNames.ORDER, "주문", Map.of("orderId", "ORD-1001")));
        context.getPlan().markCompleted(ToolNames.ORDER);

        List<String> secondWave = planner.nextBatch(context).stream()
                .map(PlanStep::getToolName).toList();
        assertEquals(List.of(ToolNames.SHIPPING), secondWave);
    }
}

package com.example.aiagent.infra.mcp;

import com.example.aiagent.config.AgentProperties;
import com.example.aiagent.infra.persistence.OrderRepository;
import com.example.aiagent.infra.shipping.ShippingApiClient;
import com.example.aiagent.tool.OrderTool;
import com.example.aiagent.tool.ShippingTool;
import com.example.aiagent.tool.ToolNames;
import com.example.aiagent.tool.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * McpToolDiscovery 통합 테스트.
 *
 * <p>기동 시 실제로 벌어지는 일 — 서버 접속 → Tool 발견 → 레지스트리 등록 — 을
 * 가짜 MCP 서버를 띄워 그대로 재현한다. 특히 <b>서버가 죽었을 때도 기동이 계속되는지</b>가
 * 중요하다. 여기서 예외가 새면 운영에서 MCP 서버 장애가 곧 전체 서비스 장애가 된다.</p>
 */
class McpToolDiscoveryTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private static final String INITIALIZE = """
            {"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-06-18",
             "capabilities":{"tools":{}},
             "serverInfo":{"name":"shipping-mcp","version":"1.0.0"}}}
            """;

    private static final String TOOLS = """
            {"jsonrpc":"2.0","id":2,"result":{"tools":[
              {"name":"ShippingTool","description":"운송장 조회",
               "inputSchema":{"type":"object","properties":{"orderId":{"type":"string"}},
                              "required":["orderId"]}},
              {"name":"WarehouseTool","description":"창고 재고 조회",
               "inputSchema":{"type":"object","properties":{},"required":[]}}]}}
            """;

    private int startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/mcp", exchange -> {
            String body;
            try (InputStream in = exchange.getRequestBody()) {
                body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            String response = body.contains("tools/list") ? TOOLS : INITIALIZE;
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        return server.getAddress().getPort();
    }

    /** 하이브리드 구성: 주문은 로컬 DB Tool, 배송은 로컬 폴백 + MCP */
    private ToolRegistry localRegistry() {
        return new ToolRegistry(List.of(
                new OrderTool(mock(OrderRepository.class)),
                new ShippingTool(mock(ShippingApiClient.class))));
    }

    private AgentProperties propertiesFor(String url) {
        AgentProperties properties = new AgentProperties();
        AgentProperties.Mcp.Server server = new AgentProperties.Mcp.Server();
        server.setName("shipping");
        server.setUrl(url);
        properties.getMcp().setServers(List.of(server));
        return properties;
    }

    private McpToolDiscovery discovery(AgentProperties properties, ToolRegistry registry) {
        return new McpToolDiscovery(
                properties, registry, RestClient.builder(), new ObjectMapper());
    }

    @Test
    @DisplayName("기동 시 원격 Tool 을 발견해 등록하고, 동명 로컬 Tool 을 대체한다")
    void discoversAndRegistersRemoteTools() throws IOException {
        int port = startServer();
        ToolRegistry registry = localRegistry();

        discovery(propertiesFor("http://localhost:" + port + "/mcp"), registry).discoverTools();

        // 서버에만 있던 Tool 이 코드 변경 없이 Agent 의 능력이 되었다.
        assertTrue(registry.names().contains("WarehouseTool"),
                "새 원격 Tool 이 등록되어야 한다. 실제=" + registry.names());

        // 같은 이름은 원격이 이긴다.
        assertInstanceOf(McpToolAdapter.class, registry.get(ToolNames.SHIPPING),
                "MCP Tool 이 로컬 폴백을 대체해야 한다");

        // 의존성은 서버가 준 스키마에서 나온다.
        assertEquals(java.util.Set.of("orderId"),
                registry.get(ToolNames.SHIPPING).requiredInputs());

        // 로컬 전용 Tool 은 그대로 남는다.
        assertInstanceOf(OrderTool.class, registry.get(ToolNames.ORDER));
    }

    @Test
    @DisplayName("MCP 서버에 연결하지 못해도 기동은 계속되고 로컬 폴백이 살아 있다")
    void survivesUnreachableServer() {
        ToolRegistry registry = localRegistry();

        // 아무도 듣고 있지 않은 포트
        AgentProperties properties = propertiesFor("http://localhost:1/mcp");

        // 예외가 새어 나오면 애플리케이션이 뜨지 못한다.
        discovery(properties, registry).discoverTools();

        assertInstanceOf(ShippingTool.class, registry.get(ToolNames.SHIPPING),
                "MCP 연결 실패 시 로컬 구현이 계속 사용되어야 한다");
    }

    @Test
    @DisplayName("agent.mcp.enabled=false 면 아무 서버에도 접속하지 않는다")
    void skipsWhenDisabled() {
        ToolRegistry registry = localRegistry();
        AgentProperties properties = propertiesFor("http://localhost:1/mcp");
        properties.getMcp().setEnabled(false);

        discovery(properties, registry).discoverTools();

        assertEquals(2, registry.names().size());
        assertInstanceOf(ShippingTool.class, registry.get(ToolNames.SHIPPING));
    }
}

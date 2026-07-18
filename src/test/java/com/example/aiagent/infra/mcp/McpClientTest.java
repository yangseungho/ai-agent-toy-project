package com.example.aiagent.infra.mcp;

import com.example.aiagent.config.AgentProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * McpClient 테스트 — JDK 내장 HTTP 서버로 <b>진짜 MCP 서버를 흉내</b>낸다.
 *
 * <p>Mock 객체 대신 실제 HTTP 를 쓰는 이유: 여기서 검증하고 싶은 것이 JSON-RPC 봉투 형식,
 * 헤더(세션 ID), SSE 응답 파싱처럼 <b>와이어 레벨 규약</b>이기 때문이다. RestClient 를
 * mocking 하면 정작 틀리기 쉬운 그 부분이 검증되지 않는다.</p>
 */
class McpClientTest {

    private HttpServer server;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 서버가 받은 요청 본문들 (요청 형식 검증용) */
    private final List<JsonNode> receivedRequests = new ArrayList<>();

    /** 서버가 받은 Mcp-Session-Id 헤더들 */
    private final List<String> receivedSessionIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    /**
     * 요청 method 에 따라 응답을 만들어 주는 가짜 MCP 서버를 띄운다.
     *
     * @param responder method 이름 → 응답 본문(문자열). null 을 주면 500 을 반환한다.
     */
    private McpClient startServer(Function<String, String> responder) {
        return startServer(responder, "application/json", "session-abc");
    }

    private McpClient startServer(Function<String, String> responder,
                                  String contentType,
                                  String sessionId) {
        try {
            server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/mcp", exchange -> handle(exchange, responder, contentType, sessionId));
            server.start();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        AgentProperties.Mcp.Server config = new AgentProperties.Mcp.Server();
        config.setName("test");
        config.setUrl("http://localhost:" + server.getAddress().getPort() + "/mcp");
        config.setConnectTimeout(Duration.ofSeconds(2));
        config.setReadTimeout(Duration.ofSeconds(5));

        return new McpClient(config, "2025-06-18", RestClient.builder(), objectMapper);
    }

    private void handle(HttpExchange exchange,
                        Function<String, String> responder,
                        String contentType,
                        String sessionId) throws IOException {
        String body;
        try (InputStream in = exchange.getRequestBody()) {
            body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        JsonNode request = objectMapper.readTree(body);
        receivedRequests.add(request);
        receivedSessionIds.add(exchange.getRequestHeaders().getFirst("Mcp-Session-Id"));

        String method = request.path("method").asText();
        String response = responder.apply(method);

        if (response == null) {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
            return;
        }

        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        if (sessionId != null && "initialize".equals(method)) {
            exchange.getResponseHeaders().set("Mcp-Session-Id", sessionId);
        }
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    // --- 표준 응답 픽스처 ------------------------------------------------------

    private static final String INITIALIZE_RESULT = """
            {"jsonrpc":"2.0","id":1,"result":{
              "protocolVersion":"2025-06-18",
              "capabilities":{"tools":{}},
              "serverInfo":{"name":"shipping-mcp","version":"1.0.0"}}}
            """;

    private static final String TOOLS_LIST_RESULT = """
            {"jsonrpc":"2.0","id":2,"result":{"tools":[
              {"name":"ShippingTool",
               "description":"운송장 조회",
               "inputSchema":{"type":"object",
                 "properties":{"orderId":{"type":"string"}},
                 "required":["orderId"]}},
              {"name":"WarehouseTool",
               "description":"창고 재고 조회",
               "inputSchema":{"type":"object","properties":{},"required":[]}}]}}
            """;

    private String defaultResponder(String method) {
        return switch (method) {
            case "initialize" -> INITIALIZE_RESULT;
            case "tools/list" -> TOOLS_LIST_RESULT;
            case "notifications/initialized" -> "{}";
            default -> null;
        };
    }

    // --- 테스트 ---------------------------------------------------------------

    @Test
    @DisplayName("initialize 로 핸드셰이크하고 세션 ID 를 이후 요청에 붙인다")
    void performsHandshakeAndPropagatesSession() {
        McpClient client = startServer(this::defaultResponder);

        client.initialize();
        client.listTools();

        JsonNode initialize = receivedRequests.get(0);
        assertEquals("2.0", initialize.path("jsonrpc").asText());
        assertEquals("initialize", initialize.path("method").asText());
        assertEquals("2025-06-18", initialize.path("params").path("protocolVersion").asText());

        // 첫 요청에는 세션이 없고, 그 뒤로는 서버가 준 세션을 계속 돌려줘야 한다.
        assertEquals(null, receivedSessionIds.get(0));
        assertTrue(receivedSessionIds.subList(1, receivedSessionIds.size()).stream()
                .allMatch("session-abc"::equals), "이후 요청은 세션 ID 를 포함해야 한다");
    }

    @Test
    @DisplayName("tools/list 로 Tool 을 발견하고 스키마에서 필수 인자를 읽는다")
    void discoversToolsWithSchema() {
        McpClient client = startServer(this::defaultResponder);
        client.initialize();

        List<McpToolSpec> specs = client.listTools();

        assertEquals(2, specs.size());
        assertEquals("ShippingTool", specs.get(0).name());
        assertEquals("운송장 조회", specs.get(0).description());
        // 이 값이 곧 Planner 의 의존성 그래프가 된다.
        assertEquals(java.util.Set.of("orderId"), specs.get(0).requiredArguments());
        assertTrue(specs.get(1).requiredArguments().isEmpty(), "필수 인자가 없으면 병렬 실행 가능하다");
    }

    @Test
    @DisplayName("tools/call 은 텍스트와 구조화 데이터를 함께 돌려준다")
    void callsToolAndParsesContent() {
        McpClient client = startServer(method -> switch (method) {
            case "initialize" -> INITIALIZE_RESULT;
            case "tools/call" -> """
                    {"jsonrpc":"2.0","id":3,"result":{
                      "content":[{"type":"text","text":"주문 ORD-1001 은 아직 출고 전입니다."}],
                      "structuredContent":{"orderId":"ORD-1001","status":"NOT_SHIPPED",
                                           "carrier":{"name":"한진택배"}},
                      "isError":false}}
                    """;
            default -> "{}";
        });
        client.initialize();

        McpClient.McpCallResult result = client.callTool("ShippingTool", Map.of("orderId", "ORD-1001"));

        assertFalse(result.isError());
        assertTrue(result.text().contains("출고 전"));
        assertEquals("ORD-1001", result.structured().get("orderId"));
        assertEquals("NOT_SHIPPED", result.structured().get("status"));
        // 중첩 객체는 점으로 이어 평면화된다 → Validator 가 값을 바로 비교할 수 있다.
        assertEquals("한진택배", result.structured().get("carrier.name"));

        JsonNode call = receivedRequests.get(receivedRequests.size() - 1);
        assertEquals("ShippingTool", call.path("params").path("name").asText());
        assertEquals("ORD-1001", call.path("params").path("arguments").path("orderId").asText());
    }

    @Test
    @DisplayName("isError=true 는 '실행은 됐지만 작업 실패'로 구분해 전달한다")
    void distinguishesToolExecutionError() {
        McpClient client = startServer(method -> switch (method) {
            case "initialize" -> INITIALIZE_RESULT;
            case "tools/call" -> """
                    {"jsonrpc":"2.0","id":3,"result":{
                      "content":[{"type":"text","text":"해당 운송장을 찾을 수 없습니다."}],
                      "isError":true}}
                    """;
            default -> "{}";
        });
        client.initialize();

        McpClient.McpCallResult result = client.callTool("ShippingTool", Map.of("orderId", "X"));

        assertTrue(result.isError());
        assertTrue(result.text().contains("찾을 수 없"));
    }

    @Test
    @DisplayName("JSON-RPC error 응답은 McpException 으로 올린다")
    void raisesOnJsonRpcError() {
        McpClient client = startServer(method -> switch (method) {
            case "initialize" -> INITIALIZE_RESULT;
            case "tools/call" -> """
                    {"jsonrpc":"2.0","id":3,
                     "error":{"code":-32602,"message":"Unknown tool: NopeTool"}}
                    """;
            default -> "{}";
        });
        client.initialize();

        McpException e = assertThrows(McpException.class,
                () -> client.callTool("NopeTool", Map.of()));
        assertTrue(e.getMessage().contains("Unknown tool"));
    }

    @Test
    @DisplayName("SSE 로 답하는 서버의 응답도 파싱한다")
    void parsesServerSentEventResponse() {
        // Streamable HTTP 서버는 같은 엔드포인트에서 SSE 로 답할 수 있다.
        McpClient client = startServer(
                method -> "event: message\ndata: " + INITIALIZE_RESULT.replace("\n", "") + "\n\n",
                "text/event-stream",
                "session-sse");

        client.initialize();

        assertFalse(receivedRequests.isEmpty(), "SSE 응답이어도 핸드셰이크가 성공해야 한다");
    }

    @Test
    @DisplayName("서버 장애는 McpException 으로 감싸 올린다")
    void wrapsTransportFailure() {
        McpClient client = startServer(method -> null); // 항상 500

        McpException e = assertThrows(McpException.class, client::initialize);
        assertTrue(e.getMessage().contains("호출 실패"));
    }
}

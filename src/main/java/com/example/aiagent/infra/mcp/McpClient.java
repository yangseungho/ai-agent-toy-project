package com.example.aiagent.infra.mcp;

import com.example.aiagent.config.AgentProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MCP(Model Context Protocol) 서버 클라이언트 — 서버 하나당 인스턴스 하나.
 *
 * <h2>MCP 가 무엇을 해결하는가</h2>
 * <p>Tool 을 붙일 때마다 벤더별 SDK 를 배우고 어댑터를 새로 짜는 대신, <b>Tool 을 노출하는
 * 표준 방법</b>을 정한 것이 MCP 다. 서버는 {@code tools/list} 로 "내가 뭘 할 수 있는지"를
 * 스키마와 함께 알려주고, {@code tools/call} 로 실행한다. 그래서 우리 Agent 는 상대가
 * 배송 시스템이든 사내 위키든 결제 서비스든 <b>같은 코드로</b> 붙일 수 있고,
 * 서버가 Tool 을 추가하면 우리 쪽 재배포 없이 능력이 늘어난다.</p>
 *
 * <h2>전송 방식</h2>
 * <p>JSON-RPC 2.0 메시지를 HTTP POST 로 주고받는 Streamable HTTP 트랜스포트를 쓴다.
 * 서버는 응답을 순수 JSON 으로 줄 수도 있고 SSE 스트림으로 줄 수도 있어서 둘 다 처리한다.
 * (다른 선택지인 stdio 트랜스포트는 서버를 자식 프로세스로 띄우는 방식이라
 * 원격 마이크로서비스를 부르는 이 시나리오에는 맞지 않는다.)</p>
 *
 * <p>세션이 있는 서버는 {@code initialize} 응답에 {@code Mcp-Session-Id} 를 실어 보내고,
 * 이후 모든 요청에 그 값을 되돌려 주기를 기대한다.</p>
 */
@Slf4j
public class McpClient {

    private static final String JSON_RPC_VERSION = "2.0";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String protocolVersion;
    private final AtomicLong requestId = new AtomicLong(1);

    /** 이 서버의 별칭 (로그/추적용) */
    @Getter
    private final String serverName;

    /** initialize 로 받은 세션 ID. 세션을 쓰지 않는 서버라면 null. */
    private volatile String sessionId;

    public McpClient(AgentProperties.Mcp.Server config,
                     String protocolVersion,
                     RestClient.Builder builder,
                     ObjectMapper objectMapper) {
        this.serverName = config.getName();
        this.protocolVersion = protocolVersion;
        this.objectMapper = objectMapper;

        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(config.getConnectTimeout())
                .withReadTimeout(config.getReadTimeout());

        RestClient.Builder configured = builder.clone()
                .baseUrl(config.getUrl())
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                // 서버가 JSON 으로 답할지 SSE 로 답할지 고르게 한다 (Streamable HTTP 규약).
                .defaultHeader("Accept", "application/json, text/event-stream");

        if (StringUtils.hasText(config.getApiKey())) {
            configured.defaultHeader("Authorization", "Bearer " + config.getApiKey());
        }
        this.restClient = configured.build();
    }

    /**
     * 핸드셰이크. 프로토콜 버전과 서로의 능력을 맞춘다.
     *
     * <p>MCP 는 {@code initialize} 를 마치기 전에는 다른 요청을 보내지 못하게 되어 있다.
     * 서버가 어떤 버전/기능을 지원하는지 모른 채 호출하면 해석이 어긋나기 때문이다.</p>
     */
    public void initialize() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("protocolVersion", protocolVersion);
        params.set("capabilities", objectMapper.createObjectNode());

        ObjectNode clientInfo = params.putObject("clientInfo");
        clientInfo.put("name", "fde-ai-agent");
        clientInfo.put("version", "1.0.0");

        JsonRpcExchange exchange = send("initialize", params, true);
        JsonNode result = exchange.result();

        // 세션을 쓰는 서버는 여기서 세션 ID 를 준다 → 이후 요청에 계속 붙여야 한다.
        this.sessionId = exchange.sessionId();

        log.info("[MCP:{}] 연결 완료 (server={}, protocol={}{})",
                serverName,
                result.path("serverInfo").path("name").asText("unknown"),
                result.path("protocolVersion").asText("unknown"),
                sessionId == null ? "" : ", session=" + sessionId);

        // 핸드셰이크 완료 통지 (알림이므로 응답이 없다).
        sendNotification("notifications/initialized");
    }

    /**
     * 서버가 제공하는 Tool 목록을 가져온다.
     *
     * <p>이 결과로 Agent 의 Tool 목록이 <b>런타임에</b> 결정된다.</p>
     */
    public List<McpToolSpec> listTools() {
        JsonNode result = send("tools/list", objectMapper.createObjectNode(), false).result();

        List<McpToolSpec> specs = new ArrayList<>();
        for (JsonNode tool : result.path("tools")) {
            specs.add(new McpToolSpec(
                    tool.path("name").asText(),
                    tool.path("description").asText(""),
                    tool.path("inputSchema")));
        }
        return specs;
    }

    /**
     * Tool 을 실행한다.
     *
     * <p>주의할 점: MCP 는 <b>두 종류의 실패</b>를 구분한다.</p>
     * <ul>
     *     <li>프로토콜 오류(JSON-RPC {@code error}) — Tool 이 없다, 인자가 틀렸다 등.
     *         우리 쪽 버그이거나 서버 장애다.</li>
     *     <li>실행 오류({@code result.isError = true}) — Tool 은 정상 동작했지만 작업이
     *         실패했다. 예: "그런 운송장이 없습니다".</li>
     * </ul>
     * <p>둘 다 여기서는 {@link McpException} 으로 올리되 메시지로 구분되게 한다.</p>
     */
    public McpCallResult callTool(String toolName, Map<String, String> arguments) {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", toolName);

        ObjectNode argumentsNode = params.putObject("arguments");
        arguments.forEach(argumentsNode::put);

        JsonNode result = send("tools/call", params, false).result();

        String text = extractText(result);
        boolean isError = result.path("isError").asBoolean(false);

        // structuredContent 는 기계가 읽을 수 있는 결과다. Validator 가 사실 검증에
        // 쓸 수 있도록 별도로 뽑아둔다 (텍스트만 있으면 파싱해서 추측할 수밖에 없다).
        Map<String, String> structured = flatten(result.path("structuredContent"));

        return new McpCallResult(text, structured, isError);
    }

    // --- JSON-RPC 전송 -------------------------------------------------------

    private JsonRpcExchange send(String method, JsonNode params, boolean captureSession) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("jsonrpc", JSON_RPC_VERSION);
        request.put("id", requestId.getAndIncrement());
        request.put("method", method);
        request.set("params", params);

        ResponseEntity<String> response;
        try {
            response = restClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        headers.add("MCP-Protocol-Version", protocolVersion);
                        if (sessionId != null) {
                            headers.add("Mcp-Session-Id", sessionId);
                        }
                    })
                    .body(request.toString())
                    .retrieve()
                    .toEntity(String.class);

        } catch (Exception e) {
            throw new McpException(
                    "MCP 서버(" + serverName + ") 호출 실패 [" + method + "]: " + e.getMessage(), e);
        }

        JsonNode envelope = parseBody(response.getBody(), method);

        if (envelope.has("error")) {
            JsonNode error = envelope.get("error");
            throw new McpException("MCP 서버(" + serverName + ") 오류 [" + method + "]: "
                    + error.path("message").asText() + " (code=" + error.path("code").asInt() + ")");
        }

        String session = captureSession ? response.getHeaders().getFirst("Mcp-Session-Id") : sessionId;
        return new JsonRpcExchange(envelope.path("result"), session);
    }

    /** 알림(notification)은 id 가 없고 응답도 기대하지 않는다. */
    private void sendNotification(String method) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("jsonrpc", JSON_RPC_VERSION);
        request.put("method", method);
        request.set("params", objectMapper.createObjectNode());

        try {
            restClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        headers.add("MCP-Protocol-Version", protocolVersion);
                        if (sessionId != null) {
                            headers.add("Mcp-Session-Id", sessionId);
                        }
                    })
                    .body(request.toString())
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            // 통지 실패는 치명적이지 않다. 대부분의 서버는 이것 없이도 동작한다.
            log.warn("[MCP:{}] {} 통지 실패: {}", serverName, method, e.getMessage());
        }
    }

    /**
     * 응답 본문을 JSON-RPC 봉투로 파싱한다.
     *
     * <p>Streamable HTTP 서버는 같은 엔드포인트에서 순수 JSON 또는 SSE 스트림으로 답할 수 있다.
     * SSE 면 {@code data:} 줄에 JSON 이 실려 오므로 그 줄만 뽑아 쓴다.</p>
     */
    private JsonNode parseBody(String body, String method) {
        if (!StringUtils.hasText(body)) {
            throw new McpException("MCP 서버(" + serverName + ") 가 빈 응답을 반환했다 [" + method + "]");
        }

        String json = body.trim();
        if (json.startsWith("event:") || json.startsWith("data:")) {
            json = extractSseData(body);
        }

        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new McpException(
                    "MCP 서버(" + serverName + ") 응답 파싱 실패 [" + method + "]: " + e.getMessage(), e);
        }
    }

    /** SSE 본문에서 마지막 data: 줄을 꺼낸다 (JSON-RPC 응답은 하나의 이벤트로 온다). */
    private String extractSseData(String body) {
        String data = null;
        for (String line : body.split("\n")) {
            if (line.startsWith("data:")) {
                data = line.substring("data:".length()).trim();
            }
        }
        if (data == null) {
            throw new McpException("MCP 서버(" + serverName + ") SSE 응답에 data 가 없다");
        }
        return data;
    }

    /** content 블록들에서 사람이 읽을 수 있는 텍스트만 이어 붙인다. */
    private String extractText(JsonNode result) {
        StringBuilder sb = new StringBuilder();
        for (JsonNode block : result.path("content")) {
            if ("text".equals(block.path("type").asText())) {
                if (!sb.isEmpty()) {
                    sb.append("\n");
                }
                sb.append(block.path("text").asText());
            }
        }
        return sb.toString();
    }

    /** 중첩 JSON 을 Tool 결과용 평면 Map 으로 만든다 (중첩 키는 점으로 잇는다). */
    private Map<String, String> flatten(JsonNode node) {
        Map<String, String> flat = new java.util.LinkedHashMap<>();
        flatten("", node, flat);
        return flat;
    }

    private void flatten(String prefix, JsonNode node, Map<String, String> target) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            node.properties().forEach(entry ->
                    flatten(prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey(),
                            entry.getValue(), target));
        } else if (node.isArray()) {
            int index = 0;
            for (JsonNode item : node) {
                flatten(prefix + "[" + index++ + "]", item, target);
            }
        } else if (!prefix.isEmpty()) {
            target.put(prefix, node.asText());
        }
    }

    /** JSON-RPC 응답 + 세션 헤더. */
    private record JsonRpcExchange(JsonNode result, String sessionId) {
    }

    /**
     * tools/call 결과.
     *
     * @param text       사람이 읽는 텍스트 (프롬프트 근거로 들어간다)
     * @param structured 기계가 읽는 구조화 데이터 (Validator 의 사실 검증에 쓰인다)
     * @param isError    Tool 실행 자체가 실패했는가
     */
    public record McpCallResult(String text, Map<String, String> structured, boolean isError) {
    }
}

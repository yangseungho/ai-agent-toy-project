package com.example.aiagent.infra.mcp;

import com.example.aiagent.config.AgentProperties;
import com.example.aiagent.tool.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * 기동 시 설정된 MCP 서버에 접속해 Tool 을 <b>발견하고 등록</b>한다.
 *
 * <p>기존 방식과의 차이가 여기서 드러난다. 예전에는 Tool 이 컴파일 타임에 고정이었다 —
 * 배송 조회를 붙이려면 클래스를 만들고 배포해야 했다. 이제는 MCP 서버 주소만 설정에
 * 추가하면 그 서버가 가진 Tool 이 전부 Agent 의 능력이 된다.</p>
 *
 * <h2>실패해도 기동은 계속된다</h2>
 * <p>MCP 서버 하나가 죽었다고 애플리케이션이 못 뜨면 안 된다. 연결에 실패하면 경고만
 * 남기고 넘어가며, 그 서버의 Tool 은 등록되지 않는다. Planner 는 등록되지 않은 Tool 을
 * 계획에서 덜어내므로(그 이유도 추적 로그에 남는다), Agent 는 남은 정보로 답변을 시도한다.
 * 배송 조회처럼 로컬 폴백 구현이 있는 경우에는 로컬 Tool 이 그대로 살아 있게 된다.</p>
 */
@Slf4j
@Component
public class McpToolDiscovery {

    private final AgentProperties.Mcp config;
    private final ToolRegistry toolRegistry;
    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;

    public McpToolDiscovery(AgentProperties properties,
                            ToolRegistry toolRegistry,
                            RestClient.Builder restClientBuilder,
                            ObjectMapper objectMapper) {
        this.config = properties.getMcp();
        this.toolRegistry = toolRegistry;
        this.restClientBuilder = restClientBuilder;
        this.objectMapper = objectMapper;
    }

    /**
     * 애플리케이션이 완전히 뜬 뒤에 실행한다.
     *
     * <p>빈 생성 시점이 아니라 {@link ApplicationReadyEvent} 를 쓰는 이유: 외부 네트워크
     * 호출이 빈 초기화 과정에 끼어들면, 상대 서버가 느릴 때 기동 자체가 지연되거나
     * 실패한다. 부팅과 외부 연동은 분리하는 편이 안전하다.</p>
     */
    @EventListener(ApplicationReadyEvent.class)
    public void discoverTools() {
        if (!config.isEnabled()) {
            log.info("[MCP] 비활성화됨 (agent.mcp.enabled=false) → 로컬 Tool 만 사용한다");
            return;
        }
        if (config.getServers().isEmpty()) {
            log.info("[MCP] 설정된 서버가 없다 → 로컬 Tool 만 사용한다");
            return;
        }

        for (AgentProperties.Mcp.Server server : config.getServers()) {
            connectAndRegister(server);
        }
    }

    private void connectAndRegister(AgentProperties.Mcp.Server server) {
        try {
            McpClient client = new McpClient(
                    server, config.getProtocolVersion(), restClientBuilder, objectMapper);

            client.initialize();
            List<McpToolSpec> specs = client.listTools();

            for (McpToolSpec spec : specs) {
                toolRegistry.register(new McpToolAdapter(client, spec));
                log.info("[MCP:{}] Tool 발견: {} (필요 입력={}) — {}",
                        server.getName(), spec.name(),
                        spec.requiredArguments(), spec.description());
            }
            log.info("[MCP:{}] Tool {}개 등록 완료", server.getName(), specs.size());

        } catch (Exception e) {
            // 여기서 예외를 던지면 애플리케이션이 뜨지 않는다. 부분 실패를 허용한다.
            log.warn("[MCP:{}] 연결 실패 → 이 서버의 Tool 은 사용할 수 없다 ({}). "
                            + "로컬 폴백 Tool 이 있으면 그것이 계속 사용된다.",
                    server.getName(), e.getMessage());
        }
    }
}

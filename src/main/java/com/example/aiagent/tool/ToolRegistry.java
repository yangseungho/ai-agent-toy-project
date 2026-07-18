package com.example.aiagent.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tool 이름 → Tool 인스턴스 조회.
 *
 * <p>등록 경로가 두 가지다.</p>
 * <ol>
 *     <li><b>기동 시 정적 등록</b> — Spring 이 발견한 모든 {@link Tool} 빈.
 *         새 로컬 Tool 은 {@code @Component} 만 붙이면 자동 등록된다.</li>
 *     <li><b>런타임 동적 등록</b> — MCP 서버가 {@code tools/list} 로 알려준 원격 Tool
 *         ({@link #register}). 애플리케이션을 다시 빌드하지 않고도 Agent 의 능력이 늘어난다.</li>
 * </ol>
 *
 * <p>동적 등록은 기동 이후에도 일어날 수 있고(MCP 서버 재연결) 조회는 요청 스레드들이
 * 동시에 수행하므로 {@link ConcurrentHashMap} 을 쓴다.</p>
 */
@Slf4j
@Component
public class ToolRegistry {

    private final Map<String, Tool> toolsByName = new ConcurrentHashMap<>();

    public ToolRegistry(List<Tool> tools) {
        for (Tool tool : tools) {
            toolsByName.put(tool.name(), tool);
        }
    }

    /**
     * Tool 을 등록한다. 같은 이름이 이미 있으면 <b>덮어쓴다</b>.
     *
     * <p>덮어쓰기를 허용하는 이유: 같은 이름의 로컬 Tool 과 MCP Tool 이 함께 있을 때
     * 원격(MCP) 쪽을 정답으로 본다. 로컬 구현은 MCP 서버에 연결하지 못했을 때를 위한
     * 폴백으로 남는다 — 배송 조회처럼 이미 MCP 서버로 옮겨간 기능이 서버 장애 시에도
     * 완전히 죽지 않게 하기 위함이다.</p>
     */
    public void register(Tool tool) {
        Tool previous = toolsByName.put(tool.name(), tool);
        if (previous != null) {
            log.info("[ToolRegistry] Tool 교체: {} ({} → {})",
                    tool.name(),
                    previous.getClass().getSimpleName(),
                    tool.getClass().getSimpleName());
        } else {
            log.info("[ToolRegistry] Tool 등록: {} ({})",
                    tool.name(), tool.getClass().getSimpleName());
        }
    }

    public Tool get(String toolName) {
        Tool tool = toolsByName.get(toolName);
        if (tool == null) {
            throw new IllegalArgumentException("등록되지 않은 Tool 입니다: " + toolName);
        }
        return tool;
    }

    /**
     * Tool 을 조회하되 없으면 비어 있는 Optional 을 준다.
     *
     * <p>Planner 는 예외 대신 이 메서드를 쓴다. MCP 서버가 죽어 Tool 이 등록되지 않은
     * 상황에서 대화 전체를 실패시키는 대신 "그 정보는 확인할 수 없다"로 이어가야 하기 때문이다.</p>
     */
    public Optional<Tool> find(String toolName) {
        return Optional.ofNullable(toolsByName.get(toolName));
    }

    /** 등록된 모든 Tool 이름. */
    public List<String> names() {
        return List.copyOf(toolsByName.keySet());
    }

    /** 등록된 모든 Tool. */
    public List<Tool> all() {
        return List.copyOf(toolsByName.values());
    }
}

package com.example.aiagent.tool;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tool 이름 → Tool 인스턴스를 찾아주는 레지스트리.
 *
 * <p>Spring 이 등록한 모든 {@link Tool} 빈을 주입받아 이름으로 조회할 수 있게 한다.
 * Agent Loop 는 Planner 가 지목한 Tool 이름을 이 레지스트리로 실제 Tool 로 변환한다.</p>
 */
@Component
public class ToolRegistry {

    private final Map<String, Tool> toolsByName = new LinkedHashMap<>();

    /** Spring 이 모든 Tool 구현체를 List 로 주입한다. */
    public ToolRegistry(List<Tool> tools) {
        for (Tool tool : tools) {
            toolsByName.put(tool.name(), tool);
        }
    }

    /**
     * 이름으로 Tool 을 찾는다.
     *
     * @throws IllegalArgumentException 등록되지 않은 이름인 경우
     */
    public Tool get(String toolName) {
        Tool tool = toolsByName.get(toolName);
        if (tool == null) {
            throw new IllegalArgumentException("등록되지 않은 Tool 입니다: " + toolName);
        }
        return tool;
    }
}

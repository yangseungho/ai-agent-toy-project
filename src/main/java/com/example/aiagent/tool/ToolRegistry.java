package com.example.aiagent.tool;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tool 이름 → Tool 인스턴스 조회.
 *
 * <p>Spring 이 등록한 모든 {@link Tool} 빈을 주입받는다. 새 Tool 을 추가하면
 * {@code @Component} 만 붙이면 자동으로 등록된다.</p>
 */
@Component
public class ToolRegistry {

    private final Map<String, Tool> toolsByName = new LinkedHashMap<>();

    public ToolRegistry(List<Tool> tools) {
        for (Tool tool : tools) {
            toolsByName.put(tool.name(), tool);
        }
    }

    public Tool get(String toolName) {
        Tool tool = toolsByName.get(toolName);
        if (tool == null) {
            throw new IllegalArgumentException("등록되지 않은 Tool 입니다: " + toolName);
        }
        return tool;
    }

    /** 등록된 모든 Tool 이름. */
    public List<String> names() {
        return List.copyOf(toolsByName.keySet());
    }
}

package com.example.aiagent.infra.mcp;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * MCP 서버가 {@code tools/list} 로 알려준 Tool 하나의 명세.
 *
 * @param name        Tool 이름 (서버가 정한 이름)
 * @param description 이 Tool 이 무엇을 하는지 — 원래는 LLM 에게 보여주기 위한 설명이다
 * @param inputSchema 입력 파라미터의 JSON Schema
 */
public record McpToolSpec(String name, String description, JsonNode inputSchema) {

    /**
     * 이 Tool 이 <b>반드시</b> 받아야 하는 파라미터 이름 (JSON Schema 의 {@code required}).
     *
     * <p>Planner 의 의존성 그래프가 여기서 나온다. 예를 들어 배송 조회 MCP Tool 이
     * {@code required: ["orderId"]} 라고 선언하면, orderId 를 만들어내는 Tool 이
     * 먼저 실행되어야 한다는 사실이 <b>스키마만 보고</b> 도출된다. 원격 Tool 의 의존성을
     * 우리 코드에 하드코딩하지 않아도 된다는 뜻이다.</p>
     */
    public Set<String> requiredArguments() {
        Set<String> required = new LinkedHashSet<>();
        if (inputSchema == null) {
            return required;
        }
        JsonNode requiredNode = inputSchema.path("required");
        if (requiredNode.isArray()) {
            requiredNode.forEach(node -> required.add(node.asText()));
        }
        return required;
    }
}

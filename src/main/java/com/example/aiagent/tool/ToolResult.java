package com.example.aiagent.tool;

import lombok.Getter;
import lombok.ToString;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tool 실행 결과.
 *
 * <p>교육용으로 단순하게, 결과 데이터를 {@code Map<String,String>} 형태로 담는다.
 * (예: OrderTool 결과 → {@code {"orderId":"ORD-1001", "status":"ORDERED"}})</p>
 *
 * <p>Planner 는 이 결과를 보고 다음 Tool 을 결정하고, PromptBuilder 는 이 결과를
 * 모아 Prompt 를 만들며, Validator 는 이 결과와 LLM 응답의 모순을 검사한다.</p>
 */
@Getter
@ToString
public class ToolResult {

    /** 결과를 생성한 Tool 이름 */
    private final String toolName;

    /** 사람이 읽을 수 있는 한 줄 요약 (Prompt 에 그대로 넣기 좋다) */
    private final String summary;

    /** 구조화된 결과 데이터 */
    private final Map<String, String> data;

    public ToolResult(String toolName, String summary, Map<String, String> data) {
        this.toolName = toolName;
        this.summary = summary;
        // 순서를 보존하기 위해 LinkedHashMap 으로 복사한다.
        this.data = new LinkedHashMap<>(data);
    }

    /** 특정 키의 값을 반환한다. 없으면 null. */
    public String get(String key) {
        return data.get(key);
    }
}

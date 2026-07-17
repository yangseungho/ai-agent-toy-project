package com.example.aiagent.tool;

import lombok.Getter;
import lombok.ToString;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tool 실행 결과.
 *
 * <p>실제 외부 I/O(DB, 외부 API, Vector DB)를 수행하므로 <b>실패할 수 있다</b>.
 * 따라서 성공/실패를 명시적으로 표현한다. Agent 는 일부 Tool 이 실패해도
 * 나머지 정보로 답변을 시도하고, Validator/프롬프트는 "이 정보는 조회 실패"임을
 * 알 수 있어야 환각을 막을 수 있다.</p>
 */
@Getter
@ToString
public class ToolResult {

    private final String toolName;

    /** 외부 I/O 성공 여부 */
    private final boolean success;

    /** 프롬프트에 넣기 좋은 한 줄 요약 (사실만 기술) */
    private final String summary;

    /** 구조화된 결과 데이터 (Validator 가 사실 검증에 사용) */
    private final Map<String, String> data;

    /** 실패 사유 (성공 시 null) */
    private final String errorMessage;

    private ToolResult(String toolName, boolean success, String summary,
                       Map<String, String> data, String errorMessage) {
        this.toolName = toolName;
        this.success = success;
        this.summary = summary;
        this.data = new LinkedHashMap<>(data);
        this.errorMessage = errorMessage;
    }

    /** 조회 성공. */
    public static ToolResult success(String toolName, String summary, Map<String, String> data) {
        return new ToolResult(toolName, true, summary, data, null);
    }

    /**
     * 조회는 성공했지만 데이터가 없음 (예: 주문 내역 없음).
     * 실패가 아니므로 success=true 이며, summary 에 '없음'을 명시해 모델이 지어내지 않게 한다.
     */
    public static ToolResult empty(String toolName, String summary) {
        return new ToolResult(toolName, true, summary, Map.of(), null);
    }

    /** 외부 I/O 실패 (DB 다운, API 타임아웃 등). */
    public static ToolResult failure(String toolName, String errorMessage) {
        return new ToolResult(toolName, false,
                "(" + toolName + " 조회에 실패하여 정보를 확인할 수 없습니다: " + errorMessage + ")",
                Map.of(), errorMessage);
    }

    /** 특정 키의 값. 없으면 null. */
    public String get(String key) {
        return data.get(key);
    }

    /** 데이터가 하나라도 있는지. */
    public boolean hasData() {
        return !data.isEmpty();
    }
}

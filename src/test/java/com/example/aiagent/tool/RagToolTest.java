package com.example.aiagent.tool;

import com.example.aiagent.tool.rag.PolicyRagTool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PolicyRagTool 단위 테스트.
 */
class RagToolTest {

    private final PolicyRagTool ragTool = new PolicyRagTool();

    @Test
    @DisplayName("질문에 포함된 키워드로 정책 문서를 검색한다")
    void retrieveByKeyword() {
        ToolResult result = ragTool.execute("환불 및 쿠폰 정책이 궁금합니다");

        assertEquals(ToolNames.POLICY_RAG, result.getToolName());
        // '환불', '쿠폰' 키워드가 모두 매칭되어야 한다
        assertTrue(result.getData().containsKey("환불"));
        assertTrue(result.getData().containsKey("쿠폰"));
    }

    @Test
    @DisplayName("매칭 키워드가 없으면 환불/쿠폰 기본 정책을 반환한다")
    void fallbackWhenNoMatch() {
        ToolResult result = ragTool.execute("아무 관련 없는 질문");

        assertTrue(result.getData().containsKey("환불"));
        assertTrue(result.getData().containsKey("쿠폰"));
        assertFalse(result.getSummary().isBlank());
    }
}

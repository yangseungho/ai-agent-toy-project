package com.example.aiagent.rag;

import com.example.aiagent.config.AgentProperties;
import com.example.aiagent.tool.PolicyRagTool;
import com.example.aiagent.tool.StubToolContext;
import com.example.aiagent.tool.ToolContext;
import com.example.aiagent.tool.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RAG 검색 + PolicyRagTool 테스트 — Vector DB(pgvector)는 Mockito 로 mocking 한다.
 *
 * <p>실제 pgvector 나 임베딩 모델 없이, "벡터 검색이 이런 결과를 줬을 때
 * 우리 코드가 근거를 올바르게 구성하는가"를 검증한다.</p>
 */
class PolicyRagToolTest {

    private final VectorStore vectorStore = mock(VectorStore.class);

    private PolicyRetriever retriever() {
        AgentProperties properties = new AgentProperties();
        properties.getRag().setTopK(3);
        properties.getRag().setSimilarityThreshold(0.5);
        return new PolicyRetriever(vectorStore, properties);
    }

    private ToolContext context(String question) {
        return new StubToolContext(question, "CUST-1");
    }

    @Test
    @DisplayName("검색된 정책 문서를 출처와 함께 근거로 만든다")
    void buildsGroundingWithSources() {
        Document doc = new Document(
                "주문에 사용된 쿠폰은 주문이 정상 취소되면 자동으로 복구됩니다.",
                Map.of("source", "coupon-policy.md", "type", "policy"));

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));

        ToolResult result = new PolicyRagTool(retriever()).execute(context("취소하면 쿠폰 돌려받나요?"));

        assertTrue(result.isSuccess());
        // 출처가 남아야 답변 근거를 추적할 수 있다.
        assertEquals("coupon-policy.md", result.get("doc1.source"));
        assertEquals("1", result.get("retrievedCount"));
        assertTrue(result.getSummary().contains("자동으로 복구"));
    }

    @Test
    @DisplayName("관련 문서가 없으면 '찾지 못함'을 반환한다 (아무 문서나 끼워넣지 않는다)")
    void returnsEmptyWhenNoRelevantDocs() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        ToolResult result = new PolicyRagTool(retriever()).execute(context("오늘 날씨 어때요?"));

        assertTrue(result.isSuccess());
        assertFalse(result.hasData());
        assertTrue(result.getSummary().contains("찾지 못"));
    }

    @Test
    @DisplayName("설정한 topK 와 유사도 임계값이 검색 요청에 반영된다")
    void appliesTopKAndThreshold() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        retriever().retrieve("환불 정책");

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());

        assertEquals(3, captor.getValue().getTopK());
        assertEquals(0.5, captor.getValue().getSimilarityThreshold());
        assertEquals("환불 정책", captor.getValue().getQuery());
    }

    @Test
    @DisplayName("Vector DB 장애 시 예외를 던지지 않고 실패 결과로 감싼다")
    void wrapsVectorStoreFailure() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenThrow(new RuntimeException("pgvector connection refused"));

        ToolResult result = new PolicyRagTool(retriever()).execute(context("환불 정책"));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("정책 문서 검색 실패"));
    }
}

package com.example.aiagent.rag;

import com.example.aiagent.config.AgentProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * RAG 검색(Retrieval).
 *
 * <p>{@link PolicyIngestionService} 가 "쓰기"라면 이쪽은 "읽기"다.</p>
 *
 * <pre>
 *  사용자 질문
 *      │  1) 질문을 같은 임베딩 모델로 벡터화 (Spring AI 가 자동 수행)
 *      ▼
 *  질문 벡터
 *      │  2) pgvector 에서 코사인 거리가 가까운 청크를 top-K 개 검색
 *      │     (SQL 로 치면 ORDER BY embedding <=> :queryVector LIMIT k)
 *      ▼
 *  관련 문서 청크
 * </pre>
 *
 * <p>similarityThreshold 로 관련 없는 문서를 걸러내는 것이 중요하다.
 * 벡터 검색은 항상 "가장 가까운 것"을 돌려주기 때문에, 임계값이 없으면
 * 전혀 무관한 문서까지 근거로 들어가 환각을 유발한다.</p>
 */
@Slf4j
@Service
public class PolicyRetriever {

    private final VectorStore vectorStore;
    private final AgentProperties.Rag config;

    public PolicyRetriever(VectorStore vectorStore, AgentProperties properties) {
        this.vectorStore = vectorStore;
        this.config = properties.getRag();
    }

    /**
     * 질문과 의미적으로 유사한 정책 문서 청크를 검색한다.
     *
     * @param query 사용자 질문
     * @return 유사도 임계값을 넘은 문서 청크 (없으면 빈 리스트)
     */
    public List<Document> retrieve(String query) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(config.getTopK())
                .similarityThreshold(config.getSimilarityThreshold())
                .build();

        List<Document> results = vectorStore.similaritySearch(request);

        if (results == null) {
            return List.of();
        }

        log.debug("[RAG] query='{}' → {}건 검색됨 (topK={}, threshold={})",
                query, results.size(), config.getTopK(), config.getSimilarityThreshold());
        return results;
    }
}

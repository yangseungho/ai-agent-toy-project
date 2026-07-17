package com.example.aiagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * RAG 색인(Ingestion) 파이프라인.
 *
 * <p>RAG 는 검색만 있는 게 아니다. 먼저 <b>문서를 벡터로 만들어 저장</b>해야 검색할 수 있다.
 * 이 클래스가 그 "쓰기" 절반을 담당한다.</p>
 *
 * <pre>
 *  원본 문서(.md)
 *      │  1) 읽기
 *      ▼
 *  Document
 *      │  2) 청킹(Chunking) — 긴 문서를 검색 단위로 자른다.
 *      │     문서 전체를 한 벡터로 만들면 검색 정확도가 떨어지고,
 *      │     LLM 컨텍스트에 통째로 넣게 되어 비효율적이다.
 *      ▼
 *  Document 조각들
 *      │  3) 임베딩 — EmbeddingModel 이 각 조각을 384차원 벡터로 변환 (자동 수행)
 *      ▼
 *  pgvector (vector_store 테이블)
 * </pre>
 *
 * <p>{@code vectorStore.add(...)} 를 호출하면 Spring AI 가 내부적으로 임베딩 모델을 호출해
 * 벡터를 만들고 PostgreSQL 에 INSERT 한다.</p>
 */
@Slf4j
@Service
public class PolicyIngestionService {

    private final VectorStore vectorStore;
    private final PolicyDocumentSource documentSource;
    private final TokenTextSplitter textSplitter;

    public PolicyIngestionService(VectorStore vectorStore, PolicyDocumentSource documentSource) {
        this.vectorStore = vectorStore;
        this.documentSource = documentSource;
        // 청크 크기를 토큰 단위로 자른다. 정책 문서는 조항 단위가 짧으므로 작게 잡는다.
        this.textSplitter = new TokenTextSplitter();
    }

    /**
     * 정책 문서를 읽어 청킹 → 임베딩 → pgvector 에 저장한다.
     *
     * @return 저장된 청크 수
     */
    public int ingest() {
        List<Document> documents = readDocuments();

        if (documents.isEmpty()) {
            log.warn("[RAG] 색인할 문서가 없습니다.");
            return 0;
        }

        // 청킹: 긴 문서를 검색에 적합한 크기로 자른다.
        List<Document> chunks = textSplitter.apply(documents);

        // 저장: 이 호출 안에서 임베딩 모델이 각 청크를 벡터로 변환한 뒤 pgvector 에 INSERT 된다.
        vectorStore.add(chunks);

        log.info("[RAG] 색인 완료: 원본 {}건 → 청크 {}건", documents.size(), chunks.size());
        return chunks.size();
    }

    /** 원본 문서를 읽어 Document 로 변환한다. */
    private List<Document> readDocuments() {
        List<Document> documents = new ArrayList<>();

        for (Resource resource : documentSource.loadDocuments()) {
            try {
                String text = resource.getContentAsString(StandardCharsets.UTF_8);

                // 메타데이터는 검색 결과의 출처 표시(인용)에 사용한다.
                // 실전에서 "답변의 근거가 어느 문서인지" 보여주려면 반드시 필요하다.
                Map<String, Object> metadata = Map.of(
                        "source", resource.getFilename() == null ? "unknown" : resource.getFilename(),
                        "type", "policy"
                );

                documents.add(new Document(text, metadata));

            } catch (IOException e) {
                // 문서 하나를 못 읽는다고 전체 색인을 실패시키지 않는다.
                log.error("[RAG] 문서 읽기 실패: {}", resource.getFilename(), e);
            }
        }
        return documents;
    }
}

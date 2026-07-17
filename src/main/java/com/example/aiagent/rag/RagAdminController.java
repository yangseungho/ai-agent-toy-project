package com.example.aiagent.rag;

import org.springframework.ai.document.Document;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * RAG 운영용 엔드포인트.
 *
 * <p>문서 색인을 수동 실행하고, 검색이 잘 되는지 눈으로 확인할 수 있다.
 * RAG 는 "왜 이 문서가 근거로 잡혔는지"를 확인하는 것이 디버깅의 핵심이라
 * 검색 결과를 직접 조회하는 엔드포인트가 실전에서도 매우 유용하다.</p>
 *
 * <p>실전에서는 반드시 인증/인가를 붙이고 외부에 노출하지 말 것.</p>
 */
@RestController
@RequestMapping("/api/admin/rag")
public class RagAdminController {

    private final PolicyIngestionService ingestionService;
    private final PolicyRetriever retriever;

    public RagAdminController(PolicyIngestionService ingestionService, PolicyRetriever retriever) {
        this.ingestionService = ingestionService;
        this.retriever = retriever;
    }

    /** 정책 문서를 다시 색인한다. */
    @PostMapping("/ingest")
    public ResponseEntity<Map<String, Object>> ingest() {
        int chunks = ingestionService.ingest();
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "indexedChunks", chunks
        ));
    }

    /** 벡터 검색 결과를 직접 확인한다 (RAG 디버깅용). */
    @PostMapping("/search")
    public ResponseEntity<Map<String, Object>> search(@RequestParam String query) {
        List<Document> documents = retriever.retrieve(query);

        List<Map<String, Object>> hits = documents.stream()
                .map(document -> Map.<String, Object>of(
                        "source", String.valueOf(document.getMetadata().get("source")),
                        "score", document.getScore() == null ? "n/a" : document.getScore(),
                        "text", document.getText() == null ? "" : document.getText()
                ))
                .toList();

        return ResponseEntity.ok(Map.of(
                "query", query,
                "hitCount", hits.size(),
                "hits", hits
        ));
    }
}

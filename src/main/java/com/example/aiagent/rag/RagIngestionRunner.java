package com.example.aiagent.rag;

import com.example.aiagent.config.AgentProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 기동 시 정책 문서를 자동 색인한다.
 *
 * <p>{@code agent.rag.ingest-on-startup=true} 일 때만 동작한다.</p>
 *
 * <p>주의: 실전에서는 보통 이렇게 하지 않는다. 인스턴스를 여러 대 띄우면 매번 중복
 * 색인되기 때문이다. 실제로는 별도 배치/파이프라인에서 문서가 변경될 때만 색인하고,
 * 애플리케이션은 검색만 한다. 여기서는 학습 편의를 위해 켜두었고,
 * 끄면 {@code POST /api/admin/rag/ingest} 로 수동 실행할 수 있다.</p>
 */
@Slf4j
@Component
public class RagIngestionRunner implements ApplicationRunner {

    private final PolicyIngestionService ingestionService;
    private final AgentProperties.Rag config;

    public RagIngestionRunner(PolicyIngestionService ingestionService, AgentProperties properties) {
        this.ingestionService = ingestionService;
        this.config = properties.getRag();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!config.isIngestOnStartup()) {
            log.info("[RAG] ingest-on-startup=false → 기동 시 색인을 건너뜁니다.");
            return;
        }

        try {
            int chunks = ingestionService.ingest();
            log.info("[RAG] 기동 색인 완료. 청크 {}건", chunks);
        } catch (Exception e) {
            // 색인 실패로 서버 기동 자체를 막지는 않는다.
            log.error("[RAG] 기동 색인 실패. Vector DB(pgvector) 연결을 확인하세요.", e);
        }
    }
}

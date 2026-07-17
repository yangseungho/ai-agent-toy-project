package com.example.aiagent;

import com.example.aiagent.config.AgentProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 애플리케이션 진입점.
 *
 * <p>AI Agent 아키텍처(의도분류 → 라우팅 → 계획 → Tool 호출 → RAG → 답변생성 →
 * 검증 → Reflection)를 실제 외부 연동(Claude API / PostgreSQL / pgvector / 외부 REST API)
 * 위에서 구현한 레퍼런스 프로젝트이다.</p>
 *
 * <p>교육용이지만 Mock 없이 실제 I/O 로 동작하므로, 도메인만 교체하면 실전에 그대로
 * 컨버팅할 수 있는 구조를 목표로 한다.</p>
 */
@SpringBootApplication
@EnableConfigurationProperties(AgentProperties.class)
public class AiAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiAgentApplication.class, args);
    }
}

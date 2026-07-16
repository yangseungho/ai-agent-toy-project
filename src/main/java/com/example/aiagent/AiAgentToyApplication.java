package com.example.aiagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 애플리케이션 진입점.
 *
 * <p>이 프로젝트는 실제 AI 서비스가 아니라, AI Agent 아키텍처의 전체 동작 흐름을
 * 코드로 이해하기 위한 <b>교육용 레퍼런스 프로젝트</b>이다.</p>
 *
 * <p>서버를 기동하면 {@code POST /api/agent/chat} REST API 하나가 제공되며,
 * 모든 LLM 호출과 데이터는 Mock 으로 동작한다.</p>
 */
@SpringBootApplication
public class AiAgentToyApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiAgentToyApplication.class, args);
    }
}

package com.example.aiagent.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * application.yaml 의 {@code agent.*} 설정을 타입 안전하게 바인딩한다.
 *
 * <p>외부 연결 정보(LLM API Key, 배송사 API 주소 등)를 코드에 하드코딩하지 않고
 * 모두 설정으로 분리하기 위한 클래스이다.</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {

    private Llm llm = new Llm();
    private ShippingApi shippingApi = new ShippingApi();
    private Mcp mcp = new Mcp();
    private Rag rag = new Rag();
    private Loop loop = new Loop();
    private Memory memory = new Memory();

    /** Anthropic Claude API 연결 설정 */
    @Getter
    @Setter
    public static class Llm {
        /** Anthropic API Key (환경변수 ANTHROPIC_API_KEY 주입 권장) */
        private String apiKey;
        /** 사용할 Claude 모델 ID */
        private String model = "claude-opus-4-8";
        /** 답변 생성 시 최대 토큰 (thinking 토큰 포함) */
        private int maxTokens = 16000;
        /** 의도 분류(구조화 출력) 시 최대 토큰 */
        private int classificationMaxTokens = 2048;
        /** 호출 타임아웃 */
        private Duration timeout = Duration.ofSeconds(120);
    }

    /** 외부 배송사 API 연결 설정 */
    @Getter
    @Setter
    public static class ShippingApi {
        private String baseUrl = "http://localhost:9090";
        private String apiKey = "";
        private Duration connectTimeout = Duration.ofSeconds(2);
        private Duration readTimeout = Duration.ofSeconds(3);
    }

    /**
     * MCP(Model Context Protocol) 서버 연결 설정.
     *
     * <p>여기에 서버를 추가하면 그 서버가 노출하는 Tool 이 기동 시 자동으로 발견되어
     * Agent 의 능력이 된다. <b>코드 변경 없이</b> Tool 을 늘릴 수 있다는 것이 핵심이다.</p>
     */
    @Getter
    @Setter
    public static class Mcp {
        /** MCP 연동 자체를 끌 수 있다 (로컬 개발/테스트 편의) */
        private boolean enabled = true;
        /** 클라이언트가 지원한다고 선언할 MCP 프로토콜 버전 */
        private String protocolVersion = "2025-06-18";
        /** 연결할 MCP 서버 목록 */
        private List<Server> servers = new ArrayList<>();

        @Getter
        @Setter
        public static class Server {
            /** 로그/추적용 서버 별칭 */
            private String name;
            /** MCP 엔드포인트 (Streamable HTTP 트랜스포트) */
            private String url;
            /** 필요 시 Authorization 헤더로 보낼 토큰 */
            private String apiKey;
            private Duration connectTimeout = Duration.ofSeconds(2);
            private Duration readTimeout = Duration.ofSeconds(10);
        }
    }

    /** RAG(문서 검색) 설정 */
    @Getter
    @Setter
    public static class Rag {
        /** 기동 시 정책 문서를 자동 색인할지 여부 */
        private boolean ingestOnStartup = true;
        /** 유사도 검색으로 가져올 문서 수 */
        private int topK = 3;
        /** 유사도 임계값 (이보다 낮으면 버림) */
        private double similarityThreshold = 0.5;
    }

    /** Agent Loop 설정 */
    @Getter
    @Setter
    public static class Loop {
        /** wave 반복 최대 횟수 (무한 루프 방지) */
        private int maxSteps = 10;
        /** Validation 실패 시 Reflection 재시도 횟수 */
        private int maxReflectionRetries = 1;
        /**
         * 서로 독립적인 Tool 을 병렬 실행할지 여부.
         * false 로 두면 순차 실행 — 디버깅 시 로그를 읽기 쉽게 만들 때 유용하다.
         */
        private boolean parallel = true;
        /**
         * Tool 하나당 허용 시간. 초과하면 그 Tool 만 실패 처리하고 나머지로 답변을 시도한다.
         * Tool 내부 타임아웃(RestClient 등)과 별개로 Agent 가 거는 상한이다.
         */
        private Duration toolTimeout = Duration.ofSeconds(10);
    }

    /** 대화 이력(Memory) 설정 */
    @Getter
    @Setter
    public static class Memory {
        /** 프롬프트에 주입할 최근 메시지 수 */
        private int maxHistoryMessages = 10;
    }
}

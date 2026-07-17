package com.example.aiagent.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

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
        /** Tool 호출 최대 횟수 (무한 루프 방지) */
        private int maxSteps = 10;
        /** Validation 실패 시 Reflection 재시도 횟수 */
        private int maxReflectionRetries = 1;
    }

    /** 대화 이력(Memory) 설정 */
    @Getter
    @Setter
    public static class Memory {
        /** 프롬프트에 주입할 최근 메시지 수 */
        private int maxHistoryMessages = 10;
    }
}

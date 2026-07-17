package com.example.aiagent.llm;

/**
 * LLM 호출 실패를 나타내는 예외.
 *
 * <p>벤더 SDK 예외(AnthropicServiceException 등)를 그대로 상위 계층에 노출하지 않고
 * 이 예외로 감싸서, Agent 코어가 특정 벤더에 의존하지 않도록 한다.</p>
 */
public class LlmException extends RuntimeException {

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }

    public LlmException(String message) {
        super(message);
    }
}

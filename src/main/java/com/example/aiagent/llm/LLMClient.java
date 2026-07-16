package com.example.aiagent.llm;

import com.example.aiagent.prompt.Prompt;

/**
 * LLM 호출 추상화.
 *
 * <p>요구사항: 실제 OpenAI/Spring AI 클라이언트 대신 이 인터페이스를 통해 LLM 을 호출하며,
 * 구현체는 항상 Mock({@link FakeLLMClient})이다. 실제 API 는 호출하지 않는다.</p>
 */
public interface LLMClient {

    /**
     * Prompt 를 받아 응답 텍스트를 생성한다.
     *
     * @param prompt 조립된 Prompt
     * @return 생성된 답변 문자열
     */
    String complete(Prompt prompt);
}

package com.example.aiagent.llm;

import com.example.aiagent.prompt.Prompt;

/**
 * LLM 호출 포트(Port).
 *
 * <p>Agent 코어가 특정 LLM 벤더에 직접 의존하지 않도록 하는 경계이다.
 * 구현체는 {@link ClaudeLlmClient} 하나이며 <b>실제 Anthropic API 를 호출</b>한다.
 * (Fake 구현체는 두지 않는다. 테스트에서는 이 인터페이스를 Mockito 로 mocking 한다.)</p>
 *
 * <p>다른 벤더로 교체하려면 이 인터페이스의 구현체만 추가하면 된다.</p>
 */
public interface LlmClient {

    /**
     * Prompt 를 받아 자연어 답변을 생성한다.
     *
     * @param prompt 조립된 프롬프트
     * @return 생성된 답변 텍스트
     */
    String complete(Prompt prompt);

    /**
     * 구조화된 출력(Structured Output)을 생성한다.
     *
     * <p>LLM 응답을 지정한 타입의 JSON 스키마로 강제하여 파싱 실패 없이 객체로 받는다.
     * Intent 분류처럼 "정해진 형태"가 필요한 경우에 사용한다.</p>
     *
     * @param systemInstruction 시스템 지시문
     * @param userMessage       사용자 메시지
     * @param responseType      응답을 매핑할 타입
     * @return 스키마에 맞게 파싱된 객체
     */
    <T> T completeStructured(String systemInstruction, String userMessage, Class<T> responseType);
}

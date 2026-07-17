package com.example.aiagent.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import com.example.aiagent.config.AgentProperties;
import com.example.aiagent.memory.ChatMessage;
import com.example.aiagent.prompt.Prompt;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * {@link LlmClient} 의 실제 구현체 — Anthropic Claude API 를 호출한다.
 *
 * <p>공식 Anthropic Java SDK 를 사용하며, 두 가지 호출 패턴을 보여준다.</p>
 * <ol>
 *     <li><b>일반 답변 생성</b>: adaptive thinking 을 켜서 모델이 필요한 만큼 스스로
 *         추론하도록 한다. (Claude 4.6+ 권장 방식. 고정 thinking budget 은 사용하지 않는다.)</li>
 *     <li><b>구조화 출력</b>: 응답을 JSON 스키마로 강제해 타입 객체로 바로 받는다.
 *         Intent 분류처럼 형태가 정해진 응답에 사용한다.</li>
 * </ol>
 */
@Slf4j
@Component
public class ClaudeLlmClient implements LlmClient {

    private final AnthropicClient client;
    private final AgentProperties.Llm config;

    public ClaudeLlmClient(AnthropicClient client, AgentProperties properties) {
        this.client = client;
        this.config = properties.getLlm();
    }

    @Override
    public String complete(Prompt prompt) {
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(config.getModel())
                .maxTokens(config.getMaxTokens())
                .system(prompt.getSystemInstruction())
                // adaptive thinking: 모델이 문제 난이도에 따라 스스로 사고량을 조절한다.
                .thinking(ThinkingConfigAdaptive.builder().build());

        // 이전 대화 이력(Memory)을 그대로 메시지로 넣어 멀티턴 맥락을 유지한다.
        for (ChatMessage message : prompt.getHistory()) {
            if (message.role() == ChatMessage.Role.USER) {
                builder.addUserMessage(message.content());
            } else {
                builder.addAssistantMessage(message.content());
            }
        }

        // 이번 턴 메시지 (Tool 수집 근거 + 질문, 필요 시 Reflection 교정 지시 포함)
        builder.addUserMessage(prompt.effectiveUserMessage());

        try {
            Message response = client.messages().create(builder.build());
            return extractText(response);
        } catch (RateLimitException e) {
            throw new LlmException("Claude API 호출량 제한(429)에 걸렸습니다. 잠시 후 재시도하세요.", e);
        } catch (AnthropicServiceException e) {
            throw new LlmException("Claude API 호출 실패: "
                    + e.errorType().map(Object::toString).orElse("unknown"), e);
        }
    }

    @Override
    public <T> T completeStructured(String systemInstruction, String userMessage, Class<T> responseType) {
        // outputConfig(Class) 를 지정하면 SDK 가 해당 타입의 JSON 스키마를 자동 생성하고
        // 응답을 그 스키마에 맞게 강제한 뒤 타입 객체로 파싱해준다.
        StructuredMessageCreateParams<T> params = MessageCreateParams.builder()
                .model(config.getModel())
                .maxTokens(config.getClassificationMaxTokens())
                .system(systemInstruction)
                .outputConfig(responseType)
                .addUserMessage(userMessage)
                .build();

        try {
            return client.messages().create(params).content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(typed -> typed.text())
                    .findFirst()
                    .orElseThrow(() -> new LlmException(
                            "Claude 가 구조화 응답을 반환하지 않았습니다. type=" + responseType.getSimpleName()));
        } catch (RateLimitException e) {
            throw new LlmException("Claude API 호출량 제한(429)에 걸렸습니다. 잠시 후 재시도하세요.", e);
        } catch (AnthropicServiceException e) {
            throw new LlmException("Claude 구조화 출력 호출 실패: "
                    + e.errorType().map(Object::toString).orElse("unknown"), e);
        }
    }

    /**
     * 응답에서 text 블록만 뽑아 이어붙인다.
     *
     * <p>adaptive thinking 을 켜면 응답에 thinking 블록도 함께 오는데,
     * {@code block.text()} 로 걸러내면 사용자에게 보여줄 최종 텍스트만 남는다.</p>
     */
    private String extractText(Message response) {
        String text = response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(TextBlock::text)
                .collect(Collectors.joining("\n"))
                .trim();

        if (text.isEmpty()) {
            throw new LlmException("Claude 응답에 텍스트 블록이 없습니다. stopReason=" + response.stopReason());
        }
        return text;
    }
}

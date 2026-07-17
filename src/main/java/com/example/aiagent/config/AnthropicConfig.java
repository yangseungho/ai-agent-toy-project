package com.example.aiagent.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Anthropic Claude 클라이언트 빈 등록.
 *
 * <p>공식 Anthropic Java SDK 의 {@link AnthropicClient} 를 생성한다.
 * API Key 는 application.yaml 의 {@code agent.llm.api-key} (환경변수 ANTHROPIC_API_KEY)에서 온다.</p>
 */
@Configuration
public class AnthropicConfig {

    @Bean
    public AnthropicClient anthropicClient(AgentProperties properties) {
        String apiKey = properties.getLlm().getApiKey();

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("""
                    Anthropic API Key 가 설정되지 않았습니다.
                    환경변수 ANTHROPIC_API_KEY 를 설정하거나 application.yaml 의 agent.llm.api-key 를 채워주세요.
                    예) export ANTHROPIC_API_KEY=sk-ant-...
                    """);
        }

        return AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(properties.getLlm().getTimeout())
                .build();
    }
}

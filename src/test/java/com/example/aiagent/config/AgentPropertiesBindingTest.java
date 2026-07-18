package com.example.aiagent.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * application.yaml 이 {@link AgentProperties} 로 실제로 바인딩되는지 확인한다.
 *
 * <p>설정 키 오타나 구조 불일치는 컴파일에 걸리지 않고 <b>기동 시점</b>에야 드러난다.
 * (더 나쁘게는, 조용히 기본값이 쓰여서 "설정했는데 왜 적용이 안 되지"가 된다.)
 * 그래서 실제 yaml 파일을 읽어 검증한다.</p>
 */
class AgentPropertiesBindingTest {

    private AgentProperties bind() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yaml"));
        sources.forEach(environment.getPropertySources()::addLast);

        return Binder.get(environment)
                .bind("agent", AgentProperties.class)
                .orElseThrow(() -> new AssertionError("agent.* 바인딩에 실패했다"));
    }

    @Test
    @DisplayName("MCP 서버 설정이 바인딩된다")
    void bindsMcpServers() throws IOException {
        AgentProperties.Mcp mcp = bind().getMcp();

        assertTrue(mcp.isEnabled());
        assertEquals("2025-06-18", mcp.getProtocolVersion());
        assertEquals(1, mcp.getServers().size());

        AgentProperties.Mcp.Server shipping = mcp.getServers().get(0);
        assertEquals("shipping", shipping.getName());
        assertTrue(shipping.getUrl().endsWith("/mcp"), "실제 URL=" + shipping.getUrl());
        assertEquals(Duration.ofSeconds(10), shipping.getReadTimeout());
    }

    @Test
    @DisplayName("Agent Loop 병렬 설정이 바인딩된다")
    void bindsLoopSettings() throws IOException {
        AgentProperties.Loop loop = bind().getLoop();

        assertTrue(loop.isParallel(), "기본은 병렬 실행이어야 한다");
        assertEquals(Duration.ofSeconds(10), loop.getToolTimeout());
        assertEquals(10, loop.getMaxSteps());
    }

    @Test
    @DisplayName("기존 설정(RAG/Memory/배송 폴백)도 그대로 바인딩된다")
    void bindsExistingSettings() throws IOException {
        AgentProperties properties = bind();

        assertEquals(3, properties.getRag().getTopK());
        assertEquals(10, properties.getMemory().getMaxHistoryMessages());
        assertFalse(properties.getShippingApi().getBaseUrl().isBlank());
    }
}

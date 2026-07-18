package com.example.aiagent.tool;

import com.example.aiagent.config.AgentProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ToolExecutor 테스트 — 병렬성과 실패 격리가 핵심.
 */
class ToolExecutorTest {

    private final ToolContext context = new StubToolContext("질문", "CUST-1");

    private ToolExecutor executor(Duration timeout, boolean parallel) {
        AgentProperties properties = new AgentProperties();
        properties.getLoop().setToolTimeout(timeout);
        properties.getLoop().setParallel(parallel);
        return new ToolExecutor(properties);
    }

    /** 지정한 시간만큼 기다렸다가 성공을 반환하는 Tool. */
    private Tool sleepingTool(String name, long sleepMillis) {
        return new Tool() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return "테스트용";
            }

            @Override
            public ToolResult execute(ToolContext context) {
                try {
                    Thread.sleep(sleepMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return ToolResult.success(name, name + " 완료", Map.of("key-" + name, "value"));
            }
        };
    }

    @Test
    @DisplayName("독립적인 Tool 들이 실제로 동시에 실행된다")
    void executesToolsConcurrently() {
        // 각 Tool 이 200ms 씩 대기한다. 순차라면 600ms 이상, 병렬이면 200ms 남짓이어야 한다.
        List<Tool> tools = List.of(
                sleepingTool("A", 200), sleepingTool("B", 200), sleepingTool("C", 200));

        long startedAt = System.currentTimeMillis();
        List<ToolResult> results = executor(Duration.ofSeconds(5), true).executeAll(tools, context);
        long elapsed = System.currentTimeMillis() - startedAt;

        assertEquals(3, results.size());
        assertTrue(results.stream().allMatch(ToolResult::isSuccess));
        assertTrue(elapsed < 500,
                "병렬 실행이면 순차 합계(600ms)보다 훨씬 빨라야 한다. 실제=" + elapsed + "ms");
    }

    @Test
    @DisplayName("모든 Tool 이 같은 시점에 살아 있다 (실제 동시 실행 확인)")
    void allToolsRunAtTheSameTime() throws InterruptedException {
        int toolCount = 3;
        CountDownLatch allStarted = new CountDownLatch(toolCount);
        AtomicInteger peakConcurrency = new AtomicInteger();
        AtomicInteger running = new AtomicInteger();

        List<Tool> tools = List.of("A", "B", "C").stream().map(name -> (Tool) new Tool() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return "테스트용";
            }

            @Override
            public ToolResult execute(ToolContext context) {
                peakConcurrency.accumulateAndGet(running.incrementAndGet(), Math::max);
                allStarted.countDown();
                try {
                    // 다른 Tool 도 모두 시작할 때까지 기다린다.
                    // 순차 실행이라면 여기서 영원히 못 빠져나온다 = 타임아웃으로 드러난다.
                    allStarted.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    running.decrementAndGet();
                }
                return ToolResult.success(name, "완료", Map.of());
            }
        }).toList();

        executor(Duration.ofSeconds(5), true).executeAll(tools, context);

        assertEquals(toolCount, peakConcurrency.get(), "3개 Tool 이 동시에 실행되고 있어야 한다");
    }

    @Test
    @DisplayName("느린 Tool 하나는 타임아웃 처리되고 나머지 결과는 살아남는다")
    void slowToolTimesOutWithoutKillingOthers() {
        List<Tool> tools = List.of(
                sleepingTool("빠름", 10),
                sleepingTool("느림", 3000));

        List<ToolResult> results = executor(Duration.ofMillis(300), true).executeAll(tools, context);

        assertTrue(results.get(0).isSuccess(), "빠른 Tool 은 정상 결과를 내야 한다");
        assertFalse(results.get(1).isSuccess(), "느린 Tool 은 타임아웃 실패여야 한다");
        assertTrue(results.get(1).getErrorMessage().contains("시간 초과"));
    }

    @Test
    @DisplayName("Tool 이 예외를 던져도 다른 Tool 과 대화 전체가 죽지 않는다")
    void isolatesExceptions() {
        Tool exploding = new Tool() {
            @Override
            public String name() {
                return "폭탄";
            }

            @Override
            public String description() {
                return "항상 터진다";
            }

            @Override
            public ToolResult execute(ToolContext context) {
                throw new IllegalStateException("예상치 못한 실패");
            }
        };

        List<ToolResult> results = executor(Duration.ofSeconds(5), true)
                .executeAll(List.of(exploding, sleepingTool("정상", 10)), context);

        assertFalse(results.get(0).isSuccess());
        assertTrue(results.get(0).getErrorMessage().contains("예상치 못한 실패"));
        assertTrue(results.get(1).isSuccess(), "옆 Tool 은 영향을 받지 않아야 한다");
    }

    @Test
    @DisplayName("결과 순서는 실행 완료 순서가 아니라 입력 순서를 따른다")
    void preservesInputOrder() {
        // 늦게 끝나는 Tool 을 먼저 넣는다. 완료 순서대로 담으면 순서가 뒤집힌다.
        List<Tool> tools = List.of(sleepingTool("느림", 200), sleepingTool("빠름", 10));

        List<ToolResult> results = executor(Duration.ofSeconds(5), true).executeAll(tools, context);

        // 순서가 실행마다 달라지면 프롬프트 근거 순서와 추적 로그가 재현되지 않는다.
        assertEquals("느림", results.get(0).getToolName());
        assertEquals("빠름", results.get(1).getToolName());
    }

    @Test
    @DisplayName("parallel=false 면 순차 실행한다 (디버깅용)")
    void runsSequentiallyWhenDisabled() {
        List<Tool> tools = List.of(sleepingTool("A", 150), sleepingTool("B", 150));

        long startedAt = System.currentTimeMillis();
        List<ToolResult> results = executor(Duration.ofSeconds(5), false).executeAll(tools, context);
        long elapsed = System.currentTimeMillis() - startedAt;

        assertEquals(2, results.size());
        assertTrue(elapsed >= 300, "순차 실행이면 대기 시간이 더해져야 한다. 실제=" + elapsed + "ms");
    }

    @Test
    @DisplayName("빈 목록은 스레드를 띄우지 않고 즉시 반환한다")
    void handlesEmptyBatch() {
        assertTrue(executor(Duration.ofSeconds(5), true).executeAll(List.of(), context).isEmpty());
    }

    @Test
    @DisplayName("requiredInputs 기본값은 비어 있다 = 아무것도 기다리지 않는다")
    void toolsHaveNoDependenciesByDefault() {
        assertEquals(Set.of(), sleepingTool("A", 0).requiredInputs());
    }
}

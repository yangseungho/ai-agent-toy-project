package com.example.aiagent.tool;

import com.example.aiagent.config.AgentProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Tool 묶음(wave)을 <b>동시에</b> 실행한다.
 *
 * <h2>왜 병렬인가</h2>
 * <p>Tool 은 거의 전부 I/O 대기다 — DB 조회, 외부 REST 호출, Vector DB 검색, MCP 서버 호출.
 * CPU 를 쓰는 구간이 아니라 <b>남의 응답을 기다리는</b> 구간이라, 서로 의존하지 않는
 * 조회를 직렬로 세우면 대기 시간이 그대로 더해진다. 배송 API 300ms + 쿠폰 DB 50ms 를
 * 순서대로 하면 350ms 지만 동시에 하면 300ms 다. Tool 이 늘수록 차이가 커진다.</p>
 *
 * <p>여기에 LLM 호출이 이미 수 초씩 걸린다는 점을 생각하면, Tool 구간까지 직렬로 쌓는 것은
 * 체감 응답 시간에 그대로 얹힌다.</p>
 *
 * <h2>왜 가상 스레드인가</h2>
 * <p>작업이 I/O 대기 위주라 플랫폼 스레드를 붙잡고 있을 이유가 없다. 가상 스레드(Java 21)는
 * 블로킹되는 동안 캐리어 스레드를 반납하므로, 동시 요청이 많아져도 스레드 풀 크기를
 * 튜닝할 필요가 없다. 기존 블로킹 코드(JPA, RestClient)를 리액티브로 갈아엎지 않고
 * 그대로 쓸 수 있다는 것이 실무에서 가장 큰 장점이다.</p>
 *
 * <h2>실패 격리</h2>
 * <p>한 Tool 의 실패나 지연이 다른 Tool 이나 대화 전체를 죽이면 안 된다. 그래서
 * <b>모든</b> 예외와 타임아웃을 {@link ToolResult#failure} 로 바꿔 담는다. Agent 는
 * "조회 실패"라는 사실을 알고 답변하면 되고, 이것이 모델이 추측으로 메우는 것을 막는다.</p>
 */
@Slf4j
@Component
public class ToolExecutor {

    private final Duration toolTimeout;
    private final boolean parallelEnabled;

    public ToolExecutor(AgentProperties properties) {
        this.toolTimeout = properties.getLoop().getToolTimeout();
        this.parallelEnabled = properties.getLoop().isParallel();
    }

    /**
     * 주어진 Tool 들을 동시에 실행하고 <b>전부</b> 끝나면 결과를 돌려준다.
     *
     * <p>반환 순서는 입력 순서와 같다. 실제 완료 순서로 담으면 실행할 때마다 추적 로그와
     * 프롬프트의 근거 순서가 달라져 재현이 어려워지기 때문이다.</p>
     */
    public List<ToolResult> executeAll(List<Tool> tools, ToolContext context) {
        if (tools.isEmpty()) {
            return List.of();
        }
        if (tools.size() == 1 || !parallelEnabled) {
            // 하나뿐이면 스레드를 띄울 이유가 없다.
            List<ToolResult> results = new ArrayList<>(tools.size());
            for (Tool tool : tools) {
                results.add(executeSafely(tool, context));
            }
            return results;
        }

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<ToolResult>> futures = tools.stream()
                    .map(tool -> CompletableFuture.supplyAsync(
                            () -> executeSafely(tool, context), executor))
                    .toList();

            List<ToolResult> results = new ArrayList<>(tools.size());
            for (int i = 0; i < futures.size(); i++) {
                results.add(join(futures.get(i), tools.get(i)));
            }
            return results;
        }
    }

    /**
     * 결과를 기다리되, 타임아웃이 지나면 포기한다.
     *
     * <p>타임아웃이 없으면 응답 없는 외부 API 하나가 대화 전체를 무한정 붙잡는다.
     * Tool 자체(RestClient 등)에도 타임아웃이 있지만, 그것과 별개로 Agent 쪽에서도
     * 상한을 둔다 — MCP 서버처럼 우리가 타임아웃 설정을 통제하지 못하는 Tool 도 있기 때문이다.</p>
     */
    private ToolResult join(CompletableFuture<ToolResult> future, Tool tool) {
        try {
            return future.get(toolTimeout.toMillis(), TimeUnit.MILLISECONDS);

        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("[ToolExecutor] {} 타임아웃 ({}ms 초과)", tool.name(), toolTimeout.toMillis());
            return ToolResult.failure(tool.name(),
                    "응답 시간 초과(" + toolTimeout.toMillis() + "ms)");

        } catch (ExecutionException e) {
            log.error("[ToolExecutor] {} 실행 중 예외", tool.name(), e.getCause());
            return ToolResult.failure(tool.name(), String.valueOf(e.getCause().getMessage()));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolResult.failure(tool.name(), "실행이 중단되었습니다.");
        }
    }

    /**
     * Tool 규약상 예외를 던지지 않아야 하지만, 구현 실수(NPE 등)까지 막아준다.
     * MCP 로 붙는 서드파티 Tool 은 우리가 구현을 통제하지 못하므로 이 방어가 특히 중요하다.
     */
    private ToolResult executeSafely(Tool tool, ToolContext context) {
        long startedAt = System.currentTimeMillis();
        try {
            return tool.execute(context);
        } catch (Exception e) {
            log.error("[ToolExecutor] {} 가 예외를 던졌다 (Tool 규약 위반)", tool.name(), e);
            return ToolResult.failure(tool.name(), "예상치 못한 오류: " + e.getMessage());
        } finally {
            log.debug("[ToolExecutor] {} 소요 {}ms", tool.name(), System.currentTimeMillis() - startedAt);
        }
    }
}

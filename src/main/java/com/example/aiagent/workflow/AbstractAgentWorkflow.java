package com.example.aiagent.workflow;

import com.example.aiagent.config.AgentProperties;
import com.example.aiagent.dto.AgentContext;
import com.example.aiagent.dto.AgentResponse;
import com.example.aiagent.llm.LlmClient;
import com.example.aiagent.planner.ExecutionPlan;
import com.example.aiagent.planner.PlanStep;
import com.example.aiagent.planner.Planner;
import com.example.aiagent.prompt.Prompt;
import com.example.aiagent.prompt.PromptBuilder;
import com.example.aiagent.reflection.ReflectionEngine;
import com.example.aiagent.tool.Tool;
import com.example.aiagent.tool.ToolRegistry;
import com.example.aiagent.tool.ToolResult;
import com.example.aiagent.validator.ValidationResult;
import com.example.aiagent.validator.Validator;
import lombok.extern.slf4j.Slf4j;

/**
 * 모든 Workflow 가 공유하는 <b>Agent 실행 엔진</b>.
 *
 * <p>AI Agent 의 핵심 루프가 여기 전부 들어 있다.</p>
 *
 * <pre>
 *  1) Planner 가 초기 계획 수립 (감지된 모든 의도의 Tool 합집합)
 *  2) ── Agent Loop ──────────────────────────────
 *       Planner 에게 "다음에 뭘 할까?" 를 반복해서 물어본다.
 *       Tool 을 실행하고 결과를 컨텍스트에 쌓는다.
 *       Planner 가 결과를 보고 다음 행동을 다시 정한다.
 *       (Planner 는 한 번만 호출되지 않는다 — 이게 Agent 와 단순 파이프라인의 차이다.)
 *     ────────────────────────────────────────────
 *  3) PromptBuilder 가 수집한 근거로 Prompt 조립
 *  4) LLM 호출 → 답변 생성
 *  5) Validator 가 답변 ↔ 실제 데이터 모순 검사
 *  6) 모순이면 Reflection 이 교정 지시를 붙여 재생성 (횟수 제한)
 *  7) 결과 + 추적 정보 반환
 * </pre>
 */
@Slf4j
public abstract class AbstractAgentWorkflow implements Workflow {

    private final Planner planner;
    private final ToolRegistry toolRegistry;
    private final PromptBuilder promptBuilder;
    private final LlmClient llmClient;
    private final Validator validator;
    private final ReflectionEngine reflectionEngine;
    private final AgentProperties.Loop loopConfig;

    protected AbstractAgentWorkflow(Planner planner,
                                    ToolRegistry toolRegistry,
                                    PromptBuilder promptBuilder,
                                    LlmClient llmClient,
                                    Validator validator,
                                    ReflectionEngine reflectionEngine,
                                    AgentProperties properties) {
        this.planner = planner;
        this.toolRegistry = toolRegistry;
        this.promptBuilder = promptBuilder;
        this.llmClient = llmClient;
        this.validator = validator;
        this.reflectionEngine = reflectionEngine;
        this.loopConfig = properties.getLoop();
    }

    @Override
    public AgentResponse execute(AgentContext context) {
        context.log("[Workflow] " + name() + " 시작 (intents=" + context.intents()
                + ", primary=" + context.primaryIntent() + ")");

        // 1) 초기 계획
        ExecutionPlan plan = planner.plan(context);
        context.log("[Planner] 초기 계획\n" + plan.describe());

        // 2) Agent Loop
        runAgentLoop(context);

        // 3) Prompt 조립
        Prompt prompt = promptBuilder.build(context);
        context.log("[PromptBuilder] Prompt 생성 완료");

        // 4) LLM 호출
        String answer = llmClient.complete(prompt);
        context.log("[LLM] 최초 응답 생성 완료");
        log.debug("[LLM] 최초 응답: {}", answer);

        // 5) 검증
        ValidationResult validation = validator.validate(context, answer);
        boolean reflectionTriggered = false;
        boolean validationPassed = validation.isValid();

        if (validationPassed) {
            context.log("[Validator] 통과 (실제 데이터와 모순 없음)");
        } else {
            context.log("[Validator] 실패 → " + validation.getReason());

            // 6) Reflection (횟수 제한)
            for (int attempt = 1; attempt <= loopConfig.getMaxReflectionRetries(); attempt++) {
                reflectionTriggered = true;
                context.log("[Reflection] 교정 재생성 시도 " + attempt + "회");

                answer = reflectionEngine.reflectAndRetry(context, prompt, validation.getReason());
                log.debug("[Reflection] 교정 응답: {}", answer);

                validation = validator.validate(context, answer);
                validationPassed = validation.isValid();

                if (validationPassed) {
                    context.log("[Validator] 재검증 통과");
                    break;
                }
                context.log("[Validator] 재검증 실패 → " + validation.getReason());
            }

            if (!validationPassed) {
                // 재시도해도 실패. 잘못된 정보를 고객에게 내보내느니 안전한 문구로 대체한다.
                // 실전에서는 이 지점에서 사람 상담원으로 에스컬레이션한다.
                context.log("[Validator] 재시도 후에도 실패 → 안전 응답으로 대체");
                answer = safeFallbackAnswer();
            }
        }

        // 7) 결과 조립
        return AgentResponse.builder()
                .answer(answer)
                .conversationId(context.getConversationId())
                .intents(context.intents().stream().map(Enum::name).toList())
                .primaryIntent(context.primaryIntent().name())
                .intentConfidence(context.getIntentClassification().confidence())
                .intentReasoning(context.getIntentClassification().reasoning())
                .workflow(name())
                .executedTools(context.getExecutedToolOrder())
                .validationPassed(validationPassed)
                .reflectionTriggered(reflectionTriggered)
                .trace(context.getTrace())
                .build();
    }

    /**
     * Agent Loop.
     *
     * <p>Planner 에게 반복해서 다음 행동을 묻고 Tool 을 실행한다.
     * maxSteps 안전장치로 무한 루프를 막는다 — Planner 버그나 예상 못한 상태에서
     * 루프가 돌면 LLM/외부 API 비용이 그대로 폭발하기 때문이다.</p>
     */
    private void runAgentLoop(AgentContext context) {
        int steps = 0;

        while (steps < loopConfig.getMaxSteps()) {
            PlanStep step = planner.decideNextStep(context);

            if (step.isFinish()) {
                context.log("[Planner] 추가 Tool 불필요 → 답변 생성 단계로 (" + step.getReason() + ")");
                return;
            }

            steps++;
            context.log("[Planner] 다음 행동 → " + step.getToolName() + " 호출 (" + step.getReason() + ")");

            Tool tool = toolRegistry.get(step.getToolName());
            ToolResult result = tool.execute(context);
            context.addToolResult(result);

            context.log("[Tool] " + step.getToolName()
                    + (result.isSuccess() ? " 성공: " : " 실패: ") + result.getSummary());
        }

        context.log("[Planner] 최대 실행 횟수(" + loopConfig.getMaxSteps() + ") 도달 → 루프 종료");
    }

    /** 검증을 끝내 통과하지 못했을 때 내보낼 안전한 답변. */
    private String safeFallbackAnswer() {
        return "죄송합니다. 현재 정확한 정보를 확인해 드리기 어렵습니다. "
                + "정확한 안내를 위해 고객센터 상담원에게 연결해 드리겠습니다.";
    }
}

package com.example.aiagent.workflow;

import com.example.aiagent.dto.AgentContext;
import com.example.aiagent.dto.AgentResponse;
import com.example.aiagent.llm.LLMClient;
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

/**
 * 모든 Workflow 가 공유하는 <b>Agent 파이프라인 실행 로직</b>을 담은 추상 클래스.
 *
 * <p>Refund/Shipping 등 구체 Workflow 는 "이름/담당 Intent"만 다를 뿐, 실제 실행 흐름
 * (Planner Loop → PromptBuilder → LLM → Validator → Reflection)은 동일하다.
 * 따라서 공통 흐름을 여기에 한 곳에 모아 교육용으로 전체 파이프라인을 한눈에 보이게 한다.</p>
 *
 * <p>실행 순서:</p>
 * <pre>
 * 1) Planner 가 초기 ExecutionPlan 을 세운다 (청사진)
 * 2) Agent Loop: Planner 에게 다음 단계를 반복해서 물어보며 Tool 을 실행한다
 *    (FINISH 가 나올 때까지 = "추가 Tool 필요 없음")
 * 3) PromptBuilder 가 수집 정보를 Prompt 로 조립한다
 * 4) LLM(Fake) 호출로 답변을 생성한다
 * 5) Validator 가 답변이 실제 데이터와 모순되는지 검사한다
 * 6) 모순이면 Reflection 이 Prompt 를 교정해 1회 재호출한다
 * 7) 최종 답변과 실행 추적 정보를 AgentResponse 로 반환한다
 * </pre>
 */
public abstract class AbstractWorkflow implements Workflow {

    private final Planner planner;
    private final ToolRegistry toolRegistry;
    private final PromptBuilder promptBuilder;
    private final LLMClient llmClient;
    private final Validator validator;
    private final ReflectionEngine reflectionEngine;

    protected AbstractWorkflow(Planner planner,
                               ToolRegistry toolRegistry,
                               PromptBuilder promptBuilder,
                               LLMClient llmClient,
                               Validator validator,
                               ReflectionEngine reflectionEngine) {
        this.planner = planner;
        this.toolRegistry = toolRegistry;
        this.promptBuilder = promptBuilder;
        this.llmClient = llmClient;
        this.validator = validator;
        this.reflectionEngine = reflectionEngine;
    }

    @Override
    public AgentResponse execute(AgentContext context) {
        context.log("[Workflow] " + name() + " 실행 시작 (Intent=" + context.getIntent() + ")");

        // 1) 초기 계획 수립 (청사진)
        ExecutionPlan plan = planner.plan(context);
        context.log("[Planner] 초기 계획 수립\n" + plan.describe());

        // 2) Agent Loop: Planner 가 다음 행동을 반복 결정한다
        runAgentLoop(context);

        // 3) Prompt 조립
        Prompt prompt = promptBuilder.build(context);
        context.log("[PromptBuilder] Prompt 생성 완료");

        // 4) LLM 최초 호출
        String answer = llmClient.complete(prompt);
        context.log("[LLM] 최초 응답: " + answer);

        // 5) Validation
        ValidationResult validation = validator.validate(context, answer);
        boolean reflectionTriggered = false;
        boolean validationPassed;

        if (validation.isValid()) {
            context.log("[Validator] 통과 (모순 없음)");
            validationPassed = true;
        } else {
            // 6) Reflection (최대 1회 재시도)
            context.log("[Validator] 실패 → " + validation.getReason());
            reflectionTriggered = true;

            answer = reflectionEngine.reflectAndRetry(context, prompt, validation.getReason());
            context.log("[Reflection] 교정된 재응답: " + answer);

            ValidationResult revalidation = validator.validate(context, answer);
            validationPassed = revalidation.isValid();
            context.log("[Validator] 재검증 결과: "
                    + (validationPassed ? "통과" : "실패(" + revalidation.getReason() + ")"));
        }

        // 7) 결과 조립
        return AgentResponse.builder()
                .answer(answer)
                .intent(context.getIntent().name())
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
     * <p>Planner 에게 "다음에 뭘 할까?"를 반복해서 물어본다. 필요한 Tool 을 하나씩
     * 실행하며 컨텍스트에 결과를 쌓고, Planner 가 FINISH 를 반환하면 종료한다.</p>
     */
    private void runAgentLoop(AgentContext context) {
        int safetyGuard = 0; // 무한 루프 방지용 안전장치
        while (true) {
            PlanStep step = planner.decideNextStep(context);

            if (step.isFinish()) {
                context.log("[Planner] 추가 Tool 필요 없음 → 답변 생성 단계로 이동");
                break;
            }

            if (safetyGuard++ > 20) {
                context.log("[Planner] 안전장치 발동: 루프를 강제 종료합니다.");
                break;
            }

            context.log("[Planner] 다음 행동 결정 → " + step.getToolName() + " 호출");
            Tool tool = toolRegistry.get(step.getToolName());
            ToolResult result = tool.execute(context.getQuestion());
            context.addToolResult(result);
            context.log("[Tool] " + step.getToolName() + " 실행 결과: " + result.getSummary());
        }
    }
}

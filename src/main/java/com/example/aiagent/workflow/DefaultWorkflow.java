package com.example.aiagent.workflow;

import com.example.aiagent.intent.Intent;
import com.example.aiagent.llm.LLMClient;
import com.example.aiagent.planner.Planner;
import com.example.aiagent.prompt.PromptBuilder;
import com.example.aiagent.reflection.ReflectionEngine;
import com.example.aiagent.tool.ToolRegistry;
import com.example.aiagent.validator.Validator;
import org.springframework.stereotype.Component;

/**
 * 기본(Fallback) Workflow. (Intent = UNKNOWN)
 *
 * <p>Intent 를 분류하지 못했거나 전용 Workflow 가 없는 경우 Router 가 이 Workflow 를
 * 선택한다. Planner 가 별도 Tool 을 요구하지 않으므로 곧바로 답변 생성 단계로 간다.</p>
 */
@Component
public class DefaultWorkflow extends AbstractWorkflow {

    public DefaultWorkflow(Planner planner,
                           ToolRegistry toolRegistry,
                           PromptBuilder promptBuilder,
                           LLMClient llmClient,
                           Validator validator,
                           ReflectionEngine reflectionEngine) {
        super(planner, toolRegistry, promptBuilder, llmClient, validator, reflectionEngine);
    }

    @Override
    public String name() {
        return "DefaultWorkflow";
    }

    @Override
    public Intent supportedIntent() {
        return Intent.UNKNOWN;
    }
}

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
 * 배송 상태 문의를 처리하는 Workflow. (Intent = SHIPPING)
 *
 * <p>주문/배송 정보만 확인하면 되므로 Planner 가 OrderTool, ShippingTool 만 호출한다.</p>
 */
@Component
public class ShippingWorkflow extends AbstractWorkflow {

    public ShippingWorkflow(Planner planner,
                            ToolRegistry toolRegistry,
                            PromptBuilder promptBuilder,
                            LLMClient llmClient,
                            Validator validator,
                            ReflectionEngine reflectionEngine) {
        super(planner, toolRegistry, promptBuilder, llmClient, validator, reflectionEngine);
    }

    @Override
    public String name() {
        return "ShippingWorkflow";
    }

    @Override
    public Intent supportedIntent() {
        return Intent.SHIPPING;
    }
}

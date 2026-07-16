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
 * 환불/취소 문의를 처리하는 Workflow. (Intent = REFUND)
 *
 * <p>대표 시나리오: "지난주 주문한 상품이 아직 안왔는데 취소하면 쿠폰도 돌려받을 수 있나요?"
 * → 주문/배송/정책/쿠폰 정보를 모두 확인한 뒤 답변한다.</p>
 *
 * <p>실행 흐름 자체는 {@link AbstractWorkflow} 에 공통으로 구현되어 있고,
 * 여기서는 이름과 담당 Intent 만 지정한다.</p>
 */
@Component
public class RefundWorkflow extends AbstractWorkflow {

    public RefundWorkflow(Planner planner,
                          ToolRegistry toolRegistry,
                          PromptBuilder promptBuilder,
                          LLMClient llmClient,
                          Validator validator,
                          ReflectionEngine reflectionEngine) {
        super(planner, toolRegistry, promptBuilder, llmClient, validator, reflectionEngine);
    }

    @Override
    public String name() {
        return "RefundWorkflow";
    }

    @Override
    public Intent supportedIntent() {
        return Intent.REFUND;
    }
}

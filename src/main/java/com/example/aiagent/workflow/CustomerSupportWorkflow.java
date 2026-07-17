package com.example.aiagent.workflow;

import com.example.aiagent.config.AgentProperties;
import com.example.aiagent.llm.LlmClient;
import com.example.aiagent.planner.Planner;
import com.example.aiagent.prompt.PromptBuilder;
import com.example.aiagent.reflection.ReflectionEngine;
import com.example.aiagent.tool.ToolRegistry;
import com.example.aiagent.validator.Validator;
import org.springframework.stereotype.Component;

/**
 * 고객 문의 처리 Workflow — 주문/배송/환불/쿠폰.
 *
 * <p>이 고객의 <b>실제 데이터</b>를 조회해야 답할 수 있는 문의를 담당한다.
 * (DB + 외부 배송 API + 정책 RAG 를 모두 사용)</p>
 *
 * <p>대표 시나리오: "지난주 주문한 상품이 아직 안왔는데 취소하면 쿠폰도 돌려받나요?"</p>
 */
@Component
public class CustomerSupportWorkflow extends AbstractAgentWorkflow {

    public CustomerSupportWorkflow(Planner planner,
                                   ToolRegistry toolRegistry,
                                   PromptBuilder promptBuilder,
                                   LlmClient llmClient,
                                   Validator validator,
                                   ReflectionEngine reflectionEngine,
                                   AgentProperties properties) {
        super(planner, toolRegistry, promptBuilder, llmClient, validator, reflectionEngine, properties);
    }

    @Override
    public String name() {
        return "CustomerSupportWorkflow";
    }
}

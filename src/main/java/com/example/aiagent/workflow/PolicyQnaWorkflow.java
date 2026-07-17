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
 * 정책 문의 Workflow — 일반적인 규정 질문 (RAG 중심).
 *
 * <p>"환불 정책이 어떻게 되나요?" 처럼 특정 고객의 데이터가 필요 없는 질문을 담당한다.
 * 이런 질문에 DB/외부 API 를 호출하는 것은 불필요한 비용과 지연일 뿐이다.
 * Planner 가 POLICY 의도에 대해 PolicyRagTool 만 계획하므로 자연스럽게 문서 검색만 수행된다.</p>
 *
 * <p>Intent 분류가 실패했거나(UNKNOWN) 전용 Workflow 가 없는 의도(ACCOUNT)도
 * 이 Workflow 가 fallback 으로 처리한다.</p>
 */
@Component
public class PolicyQnaWorkflow extends AbstractAgentWorkflow {

    public PolicyQnaWorkflow(Planner planner,
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
        return "PolicyQnaWorkflow";
    }
}

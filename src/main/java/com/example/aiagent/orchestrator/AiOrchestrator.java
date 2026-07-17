package com.example.aiagent.orchestrator;

import com.example.aiagent.dto.AgentContext;
import com.example.aiagent.dto.AgentResponse;
import com.example.aiagent.intent.IntentClassification;
import com.example.aiagent.intent.IntentClassifier;
import com.example.aiagent.memory.ChatMessage;
import com.example.aiagent.memory.ConversationMemory;
import com.example.aiagent.router.RuleBasedRouter;
import com.example.aiagent.workflow.Workflow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AI Orchestrator — 전체 흐름의 지휘자.
 *
 * <p>Gateway 는 오직 이 클래스만 호출한다. 여기서 모든 단계가 조율된다.</p>
 *
 * <pre>
 *   1) Memory 로드      : 이전 대화 맥락 (PostgreSQL)
 *   2) Intent 분류      : LLM 구조화 출력 (복합 의도 지원)
 *   3) Routing          : 규칙 기반 Workflow 선택
 *   4) Workflow 실행    : Planner Loop → Tool → RAG → LLM → Validator → Reflection
 *   5) Memory 저장      : 이번 턴 질문/답변 (PostgreSQL)
 * </pre>
 *
 * <p>왜 Orchestrator 를 따로 두는가? Memory 로드/저장, 의도 분류, 라우팅은 어떤
 * Workflow 를 타든 공통이다. 이걸 각 Workflow 에 흩어 놓으면 중복되고 빠뜨리기 쉽다.
 * 공통 절차는 여기, 문의 종류별 처리는 Workflow — 이렇게 나눈다.</p>
 */
@Slf4j
@Component
public class AiOrchestrator {

    private final ConversationMemory memory;
    private final IntentClassifier intentClassifier;
    private final RuleBasedRouter router;

    public AiOrchestrator(ConversationMemory memory,
                          IntentClassifier intentClassifier,
                          RuleBasedRouter router) {
        this.memory = memory;
        this.intentClassifier = intentClassifier;
        this.router = router;
    }

    /**
     * 사용자 질문을 받아 Agent 파이프라인 전체를 실행한다.
     *
     * @param conversationId 대화 세션 ID (멀티턴 맥락 유지 키)
     * @param customerId     고객 ID (DB 조회 키)
     * @param question       질문
     */
    public AgentResponse process(String conversationId, String customerId, String question) {

        // 1) 이전 대화 맥락 로드
        List<ChatMessage> history = memory.load(conversationId);

        // 2) 의도 분류 (LLM). 이전 맥락도 함께 넘겨 "그럼 취소해주세요" 같은 후속 질문을 이해시킨다.
        IntentClassification classification = intentClassifier.classify(question, history);

        AgentContext context = new AgentContext(conversationId, customerId, question, classification, history);
        context.log("[Orchestrator] 질문 수신 (conversationId=" + conversationId + ", 이전 대화 " + history.size() + "건)");
        context.log("[IntentClassifier] intents=" + classification.intents()
                + ", primary=" + classification.primaryIntent()
                + ", confidence=" + classification.confidence()
                + (classification.isComposite() ? " (복합 질의)" : ""));
        context.log("[IntentClassifier] 분류 근거: " + classification.reasoning());

        // 3) 라우팅 (규칙). 복합 의도여도 Workflow 는 primary 기준으로 하나만 선택한다.
        Workflow workflow = router.route(classification.primaryIntent());
        context.log("[Router] 선택된 Workflow: " + workflow.name());

        // 4) Workflow 실행
        AgentResponse response = workflow.execute(context);

        // 5) 이번 턴 저장 → 다음 턴에서 맥락으로 사용된다.
        memory.save(conversationId, question, response.getAnswer());

        return response;
    }
}

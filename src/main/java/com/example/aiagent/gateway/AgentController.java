package com.example.aiagent.gateway;

import com.example.aiagent.dto.AgentResponse;
import com.example.aiagent.dto.ChatRequest;
import com.example.aiagent.dto.ChatResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST 진입점.
 *
 * <p>요구사항: {@code POST /api/agent/chat} 하나를 제공한다. 컨트롤러는 HTTP 처리만
 * 담당하고, 실제 AI 로직은 {@link AiGateway} → Orchestrator 로 위임한다.</p>
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AiGateway gateway;

    public AgentController(AiGateway gateway) {
        this.gateway = gateway;
    }

    /**
     * 사용자 질문을 받아 Agent 파이프라인 실행 결과를 반환한다.
     *
     * <pre>
     * POST /api/agent/chat
     * { "question": "지난주 주문한 상품이 아직 안왔는데 취소하면 쿠폰도 돌려받을 수 있나요?" }
     * </pre>
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        AgentResponse response = gateway.handle(request.getQuestion());
        return ResponseEntity.ok(ChatResponse.from(response));
    }
}

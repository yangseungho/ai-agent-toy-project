package com.example.aiagent.gateway;

import com.example.aiagent.dto.AgentResponse;
import com.example.aiagent.dto.ChatRequest;
import com.example.aiagent.dto.ChatResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST 진입점.
 *
 * <p>컨트롤러는 HTTP 처리만 한다. AI 로직은 {@link AiGateway} → Orchestrator 로 위임한다.</p>
 *
 * <pre>
 * POST /api/agent/chat
 * {
 *   "conversationId": "conv-1",
 *   "customerId": "CUST-1",
 *   "question": "지난주 주문한 상품이 아직 안왔는데 취소하면 쿠폰도 돌려받을 수 있나요?"
 * }
 * </pre>
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AiGateway gateway;

    public AgentController(AiGateway gateway) {
        this.gateway = gateway;
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        AgentResponse response = gateway.handle(request);
        return ResponseEntity.ok(ChatResponse.from(response));
    }
}

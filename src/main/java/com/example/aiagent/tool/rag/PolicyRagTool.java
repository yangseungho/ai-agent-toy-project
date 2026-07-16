package com.example.aiagent.tool.rag;

import com.example.aiagent.tool.Tool;
import com.example.aiagent.tool.ToolNames;
import com.example.aiagent.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 정책 문서를 검색(RAG)하는 Tool.
 *
 * <p>실제 RAG 는 벡터 DB 에서 문서를 검색하지만, 여기서는 {@code Map<String,String>}
 * 형태의 아주 단순한 "문서 저장소"에서 키워드로 문서를 찾아 반환한다.
 * (요구사항: 실제 문서는 필요 없고 Map 정도로 구현)</p>
 */
@Component
public class PolicyRagTool implements Tool {

    /**
     * 매우 단순화한 정책 "문서 저장소".
     * key = 검색 키워드, value = 정책 문서 내용.
     */
    private final Map<String, String> policyDocuments = new LinkedHashMap<>();

    public PolicyRagTool() {
        policyDocuments.put("환불", "주문 취소는 배송 시작 전(NOT_SHIPPED)까지 가능하며, 취소 시 결제 수단으로 환불됩니다.");
        policyDocuments.put("쿠폰", "주문에 사용된 쿠폰은 주문이 정상 취소되면 자동으로 복구되어 재사용할 수 있습니다.");
        policyDocuments.put("배송", "배송이 시작된 이후에는 취소가 아닌 반품 절차를 따라야 합니다.");
    }

    @Override
    public String name() {
        return ToolNames.POLICY_RAG;
    }

    @Override
    public ToolResult execute(String question) {
        // 질문에 포함된 키워드로 관련 문서를 검색한다. (아주 단순한 키워드 매칭)
        StringBuilder retrieved = new StringBuilder();
        Map<String, String> data = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : policyDocuments.entrySet()) {
            String keyword = entry.getKey();
            if (question != null && question.contains(keyword)) {
                retrieved.append("[").append(keyword).append("] ").append(entry.getValue()).append("\n");
                data.put(keyword, entry.getValue());
            }
        }

        // 매칭된 문서가 없으면 환불/쿠폰 기본 정책을 반환한다. (교육용 안전장치)
        if (data.isEmpty()) {
            data.put("환불", policyDocuments.get("환불"));
            data.put("쿠폰", policyDocuments.get("쿠폰"));
            retrieved.append("[환불] ").append(policyDocuments.get("환불")).append("\n");
            retrieved.append("[쿠폰] ").append(policyDocuments.get("쿠폰")).append("\n");
        }

        String summary = "관련 정책 문서를 검색했습니다:\n" + retrieved.toString().trim();
        return new ToolResult(name(), summary, data);
    }
}

package com.example.aiagent.tool;

import com.example.aiagent.rag.PolicyRetriever;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 정책 문서 검색 Tool (RAG) — <b>pgvector 에 실제 유사도 검색</b>을 수행한다.
 *
 * <p>다른 Tool 이 "이 고객의 사실"(주문/배송/쿠폰)을 조회한다면, 이 Tool 은
 * "회사의 규칙"(정책 문서)을 조회한다. 둘 다 있어야 정확한 답이 나온다.</p>
 *
 * <p>예: "취소하면 쿠폰 돌려받나요?"<br>
 * → 사실: 이 주문은 아직 배송 전이고, 쿠폰이 사용됨 (DB/API)<br>
 * → 규칙: 배송 전이면 취소 가능하고, 취소 시 쿠폰은 복구됨 (RAG)<br>
 * → 둘을 합쳐야 "네, 취소 가능하고 쿠폰도 복구됩니다"라고 답할 수 있다.</p>
 */
@Slf4j
@Component
public class PolicyRagTool implements Tool {

    private final PolicyRetriever retriever;

    public PolicyRagTool(PolicyRetriever retriever) {
        this.retriever = retriever;
    }

    @Override
    public String name() {
        return ToolNames.POLICY_RAG;
    }

    @Override
    public String description() {
        return "질문과 의미적으로 유사한 환불/배송/쿠폰 정책 문서를 Vector DB에서 검색한다.";
    }

    @Override
    public ToolResult execute(ToolContext context) {
        try {
            List<Document> documents = retriever.retrieve(context.question());

            if (documents.isEmpty()) {
                // 관련 문서가 없으면 없다고 해야 한다.
                // 여기서 아무 문서나 끼워 넣으면 모델이 엉뚱한 규정을 근거로 답한다.
                return ToolResult.empty(name(),
                        "질문과 관련된 정책 문서를 찾지 못했습니다.");
            }

            Map<String, String> data = new LinkedHashMap<>();
            StringBuilder summary = new StringBuilder("검색된 관련 정책 문서:\n");

            int index = 1;
            for (Document document : documents) {
                String source = String.valueOf(document.getMetadata().get("source"));
                String text = document.getText() == null ? "" : document.getText().trim();

                // 출처를 함께 남긴다 → 답변의 근거 추적(인용)이 가능해진다.
                summary.append(String.format("[문서%d | 출처: %s]\n%s\n", index, source, text));
                data.put("doc" + index + ".source", source);
                data.put("doc" + index + ".score", String.valueOf(document.getScore()));
                index++;
            }

            data.put("retrievedCount", String.valueOf(documents.size()));

            return ToolResult.success(name(), summary.toString().trim(), data);

        } catch (Exception e) {
            // Vector DB 장애
            log.error("[PolicyRagTool] Vector DB 검색 실패", e);
            return ToolResult.failure(name(), "정책 문서 검색 실패: " + e.getMessage());
        }
    }
}

package com.example.aiagent.rag;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * RAG 로 색인할 원본 정책 문서의 출처.
 *
 * <p>지금은 classpath 의 마크다운 파일을 읽지만, 실전에서는 이 클래스만 바꾸면 된다.
 * (S3, Confluence, DB, CMS 등 → {@link Resource} 목록만 반환하면 나머지 파이프라인은 동일)</p>
 */
@Component
public class PolicyDocumentSource {

    private static final List<String> DOCUMENT_PATHS = List.of(
            "policies/refund-policy.md",
            "policies/shipping-policy.md",
            "policies/coupon-policy.md"
    );

    /** 색인 대상 문서들. */
    public List<Resource> loadDocuments() {
        return DOCUMENT_PATHS.stream()
                .map(path -> (Resource) new ClassPathResource(path))
                .toList();
    }
}

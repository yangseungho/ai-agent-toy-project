package com.example.aiagent.intent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * IntentClassifier(Rule 기반 의도 분류) 단위 테스트.
 */
class IntentClassifierTest {

    private final IntentClassifier classifier = new IntentClassifier();

    @Test
    @DisplayName("취소/쿠폰 키워드가 있으면 REFUND 로 분류한다 (대표 질문)")
    void classifyRefund() {
        String question = "지난주 주문한 상품이 아직 안왔는데 취소하면 쿠폰도 돌려받을 수 있나요?";
        assertEquals(Intent.REFUND, classifier.classify(question));
    }

    @Test
    @DisplayName("배송 키워드만 있으면 SHIPPING 으로 분류한다")
    void classifyShipping() {
        assertEquals(Intent.SHIPPING, classifier.classify("제 택배 배송 언제 오나요?"));
    }

    @Test
    @DisplayName("회원 키워드가 있으면 ACCOUNT 로 분류한다")
    void classifyAccount() {
        assertEquals(Intent.ACCOUNT, classifier.classify("회원 탈퇴는 어떻게 하나요?"));
    }

    @Test
    @DisplayName("아무 키워드도 없으면 UNKNOWN 으로 분류한다")
    void classifyUnknown() {
        assertEquals(Intent.UNKNOWN, classifier.classify("안녕하세요"));
    }

    @Test
    @DisplayName("null/빈 문자열은 UNKNOWN 으로 분류한다")
    void classifyBlank() {
        assertEquals(Intent.UNKNOWN, classifier.classify(null));
        assertEquals(Intent.UNKNOWN, classifier.classify("   "));
    }
}

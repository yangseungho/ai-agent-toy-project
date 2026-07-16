package com.example.aiagent.intent;

import org.springframework.stereotype.Component;

/**
 * Rule(키워드) 기반 Intent 분류기.
 *
 * <p>요구사항: 여기서는 LLM 을 사용하지 않는다. 질문에 포함된 키워드를 보고
 * 규칙적으로 Intent 를 결정한다.</p>
 *
 * <p>우선순위: REFUND(환불/취소/쿠폰) → SHIPPING(배송) → ACCOUNT(회원).
 * 예시 질문("...취소하면 쿠폰도 돌려받을 수 있나요?")은 배송 단어도 포함하지만
 * 본질은 '취소/환불'이므로 REFUND 를 우선한다.</p>
 */
@Component
public class IntentClassifier {

    public Intent classify(String question) {
        if (question == null || question.isBlank()) {
            return Intent.UNKNOWN;
        }

        // 1순위: 환불/취소/쿠폰 관련
        if (containsAny(question, "환불", "취소", "쿠폰", "반품")) {
            return Intent.REFUND;
        }

        // 2순위: 배송 관련
        if (containsAny(question, "배송", "안왔", "도착", "언제 오", "택배")) {
            return Intent.SHIPPING;
        }

        // 3순위: 회원/계정 관련
        if (containsAny(question, "회원", "계정", "로그인", "비밀번호")) {
            return Intent.ACCOUNT;
        }

        return Intent.UNKNOWN;
    }

    /** 질문에 주어진 키워드 중 하나라도 포함되어 있으면 true. */
    private boolean containsAny(String question, String... keywords) {
        for (String keyword : keywords) {
            if (question.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}

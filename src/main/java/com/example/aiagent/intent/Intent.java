package com.example.aiagent.intent;

/**
 * 사용자 질문의 의도(Intent).
 *
 * <p>Rule 기반 분류기가 이 값 중 하나를 반환하며, Router 가 이 값에 따라
 * 실행할 Workflow 를 선택한다.</p>
 */
public enum Intent {

    /** 환불/취소/쿠폰 관련 문의 */
    REFUND,

    /** 배송 상태 관련 문의 */
    SHIPPING,

    /** 회원/계정 관련 문의 */
    ACCOUNT,

    /** 분류 실패 */
    UNKNOWN
}

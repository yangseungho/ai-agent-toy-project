package com.example.aiagent.validator;

import lombok.Getter;

/**
 * 검증 결과.
 */
@Getter
public class ValidationResult {

    private final boolean valid;

    /** 실패 사유 (통과 시 null). Reflection 이 이 값을 교정 지시에 사용한다. */
    private final String reason;

    private ValidationResult(boolean valid, String reason) {
        this.valid = valid;
        this.reason = reason;
    }

    public static ValidationResult ok() {
        return new ValidationResult(true, null);
    }

    public static ValidationResult fail(String reason) {
        return new ValidationResult(false, reason);
    }
}

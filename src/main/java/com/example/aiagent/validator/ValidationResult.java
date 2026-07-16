package com.example.aiagent.validator;

import lombok.Getter;

/**
 * Validation 결과.
 */
@Getter
public class ValidationResult {

    /** 통과 여부 */
    private final boolean valid;

    /** 실패 사유 (통과 시 null) */
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

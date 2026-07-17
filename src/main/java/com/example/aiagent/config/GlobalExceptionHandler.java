package com.example.aiagent.config;

import com.example.aiagent.llm.LlmException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 전역 예외 처리.
 *
 * <p>Agent 는 여러 외부 시스템에 의존하므로 실패 모드가 다양하다. 어떤 실패가
 * 클라이언트 잘못(400)이고 어떤 것이 서버/외부 장애(503)인지 구분해 내려줘야
 * 호출하는 쪽이 재시도 여부를 판단할 수 있다.</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 잘못된 요청 (빈 question 등) */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "INVALID_REQUEST", "message", e.getMessage()));
    }

    /** Bean Validation 실패 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("요청 값이 올바르지 않습니다.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "INVALID_REQUEST", "message", message));
    }

    /**
     * LLM 호출 실패.
     *
     * <p>503 을 주는 이유: 클라이언트 잘못이 아니라 일시적 외부 장애일 가능성이 높아
     * 재시도가 의미 있기 때문이다.</p>
     */
    @ExceptionHandler(LlmException.class)
    public ResponseEntity<Map<String, String>> handleLlm(LlmException e) {
        log.error("[API] LLM 호출 실패", e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "LLM_UNAVAILABLE",
                        "message", "AI 응답 생성에 실패했습니다. 잠시 후 다시 시도해 주세요."));
    }
}

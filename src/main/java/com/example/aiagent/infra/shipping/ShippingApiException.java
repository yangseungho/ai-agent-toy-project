package com.example.aiagent.infra.shipping;

/**
 * 외부 배송사 API 호출 실패.
 */
public class ShippingApiException extends RuntimeException {

    public ShippingApiException(String message, Throwable cause) {
        super(message, cause);
    }
}

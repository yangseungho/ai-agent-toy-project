package com.example.aiagent.infra.shipping;

import com.example.aiagent.config.AgentProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.Optional;

/**
 * 외부 배송사 API 클라이언트 (실제 HTTP 호출).
 *
 * <p>Agent 의 Tool 이 항상 DB만 보는 것은 아니다. 실전에서는 다른 팀의 마이크로서비스나
 * 외부 벤더 API 를 호출하는 경우가 더 많고, 그때 핵심은 <b>타임아웃과 장애 처리</b>다.
 * LLM 호출 자체가 이미 수 초씩 걸리기 때문에, 외부 API 가 응답 없이 매달려 있으면
 * 사용자 체감 지연이 그대로 누적된다. 그래서 타임아웃을 짧게 건다.</p>
 *
 * <p>상태 구분도 중요하다.</p>
 * <ul>
 *     <li>404 → 아직 배송 정보가 생성되지 않음 (정상 상황, '없음')</li>
 *     <li>5xx / 타임아웃 → 조회 실패 (예외)</li>
 * </ul>
 * 이 둘을 뭉뚱그리면 "배송 정보가 없다"와 "확인할 수 없다"를 구분 못 해 모델이 잘못 답한다.
 */
@Slf4j
@Component
public class ShippingApiClient {

    private final RestClient restClient;

    public ShippingApiClient(RestClient.Builder builder, AgentProperties properties) {
        AgentProperties.ShippingApi config = properties.getShippingApi();

        // 타임아웃 설정: 외부 API 가 느릴 때 Agent 전체가 끌려가지 않도록 짧게 건다.
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(config.getConnectTimeout())
                .withReadTimeout(config.getReadTimeout());

        this.restClient = builder
                .baseUrl(config.getBaseUrl())
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                // 외부 API 인증 헤더. 실제 배송사 스펙에 맞게 교체하면 된다.
                .defaultHeader("X-API-KEY", config.getApiKey())
                .build();
    }

    /**
     * 주문번호로 배송 정보를 조회한다.
     *
     * @return 배송 정보. 아직 배송 정보가 없으면(404) {@link Optional#empty()}
     * @throws ShippingApiException 타임아웃 / 5xx 등 조회 자체가 실패한 경우
     */
    public Optional<ShippingApiResponse> findByOrderId(String orderId) {
        try {
            ResponseEntity<ShippingApiResponse> entity = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/shipments")
                            .queryParam("orderId", orderId)
                            .build())
                    .retrieve()
                    // 4xx 를 기본 예외로 던지지 않고 직접 판단한다.
                    .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                        // 아무것도 하지 않음 → 아래에서 상태코드로 분기
                    })
                    .toEntity(ShippingApiResponse.class);

            if (entity.getStatusCode().value() == 404) {
                log.debug("[ShippingApi] 배송 정보 없음 orderId={}", orderId);
                return Optional.empty();
            }

            if (entity.getStatusCode().isError()) {
                throw new ShippingApiException(
                        "배송사 API 오류 응답: HTTP " + entity.getStatusCode().value(), null);
            }

            return Optional.ofNullable(entity.getBody());

        } catch (ResourceAccessException e) {
            // 연결 실패 / 타임아웃
            log.error("[ShippingApi] 연결 실패 또는 타임아웃 orderId={}", orderId, e);
            throw new ShippingApiException("배송사 API 연결 실패(타임아웃): " + e.getMessage(), e);

        } catch (ShippingApiException e) {
            throw e;

        } catch (Exception e) {
            // 5xx 등 나머지
            log.error("[ShippingApi] 호출 실패 orderId={}", orderId, e);
            throw new ShippingApiException("배송사 API 호출 실패: " + e.getMessage(), e);
        }
    }
}

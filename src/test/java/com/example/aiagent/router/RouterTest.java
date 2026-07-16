package com.example.aiagent.router;

import com.example.aiagent.intent.Intent;
import com.example.aiagent.workflow.DefaultWorkflow;
import com.example.aiagent.workflow.Workflow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * RuleBasedRouter 단위 테스트. (Mockito 로 Workflow 를 가짜로 만든다)
 */
class RouterTest {

    @Test
    @DisplayName("Intent 에 맞는 Workflow 로 라우팅한다")
    void routeToMatchingWorkflow() {
        Workflow refund = mock(Workflow.class);
        when(refund.supportedIntent()).thenReturn(Intent.REFUND);

        Workflow shipping = mock(Workflow.class);
        when(shipping.supportedIntent()).thenReturn(Intent.SHIPPING);

        DefaultWorkflow defaultWorkflow = mock(DefaultWorkflow.class);
        when(defaultWorkflow.supportedIntent()).thenReturn(Intent.UNKNOWN);

        RuleBasedRouter router =
                new RuleBasedRouter(List.of(refund, shipping, defaultWorkflow), defaultWorkflow);

        assertEquals(refund, router.route(Intent.REFUND));
        assertEquals(shipping, router.route(Intent.SHIPPING));
    }

    @Test
    @DisplayName("매핑되지 않은 Intent 는 기본 Workflow 로 라우팅한다")
    void routeToDefault() {
        Workflow refund = mock(Workflow.class);
        when(refund.supportedIntent()).thenReturn(Intent.REFUND);

        DefaultWorkflow defaultWorkflow = mock(DefaultWorkflow.class);
        when(defaultWorkflow.supportedIntent()).thenReturn(Intent.UNKNOWN);

        RuleBasedRouter router =
                new RuleBasedRouter(List.of(refund, defaultWorkflow), defaultWorkflow);

        // ACCOUNT 전용 Workflow 가 없으므로 기본 Workflow 로 라우팅되어야 한다
        assertEquals(defaultWorkflow, router.route(Intent.ACCOUNT));
    }
}

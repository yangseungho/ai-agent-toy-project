package com.example.aiagent.tool;

/**
 * Tool 이름 상수 모음.
 *
 * <p>Planner, Workflow, Validator 등 여러 곳에서 Tool 을 문자열 이름으로 참조하기 때문에
 * 오타를 막기 위해 상수로 모아둔다.</p>
 */
public final class ToolNames {

    public static final String ORDER = "OrderTool";
    public static final String SHIPPING = "ShippingTool";
    public static final String COUPON = "CouponTool";
    public static final String POLICY_RAG = "PolicyRagTool";

    private ToolNames() {
        // 상수 전용 클래스이므로 인스턴스화 금지
    }
}

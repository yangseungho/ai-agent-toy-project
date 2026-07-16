package com.example.aiagent.tool;

/**
 * Agent 가 호출할 수 있는 Tool 의 공통 인터페이스.
 *
 * <p>실제 Agent 에서는 외부 API/DB 를 호출하겠지만, 이 프로젝트에서는 모두
 * Mock 데이터를 반환한다. Tool 은 사용자 질문(question)만 입력으로 받는다.</p>
 */
public interface Tool {

    /** 이 Tool 의 고유 이름 (Planner 가 이 이름으로 Tool 을 지목한다). */
    String name();

    /**
     * Tool 을 실행하고 결과를 반환한다.
     *
     * @param question 사용자 질문
     * @return Mock 실행 결과
     */
    ToolResult execute(String question);
}

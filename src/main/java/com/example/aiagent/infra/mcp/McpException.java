package com.example.aiagent.infra.mcp;

/**
 * MCP 서버 통신 실패.
 *
 * <p>연결 실패/타임아웃/JSON-RPC 오류 응답을 모두 이 예외로 모은다.
 * Tool 실행 경로에서는 {@code McpToolAdapter} 가 이를 잡아
 * {@code ToolResult.failure} 로 바꾸므로 대화가 죽지 않는다.</p>
 */
public class McpException extends RuntimeException {

    public McpException(String message) {
        super(message);
    }

    public McpException(String message, Throwable cause) {
        super(message, cause);
    }
}

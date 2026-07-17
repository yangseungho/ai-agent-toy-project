package com.example.aiagent.memory;

/**
 * 대화 한 턴 (프롬프트에 주입되는 형태).
 *
 * <p>JPA 엔티티({@code ConversationMessageEntity})와 분리해 둔 이유는,
 * 저장소 모델과 LLM 프롬프트 모델의 관심사를 나누기 위해서다.</p>
 *
 * @param role    작성자
 * @param content 메시지 내용
 */
public record ChatMessage(Role role, String content) {

    public enum Role {
        USER,
        ASSISTANT
    }

    public static ChatMessage user(String content) {
        return new ChatMessage(Role.USER, content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage(Role.ASSISTANT, content);
    }
}

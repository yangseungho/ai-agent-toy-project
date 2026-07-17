package com.example.aiagent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 대화 이력 한 건 (PostgreSQL {@code conversation_messages} 테이블).
 *
 * <p>Agent 의 Memory. conversationId 로 묶인 멀티턴 대화를 영속화하여,
 * 다음 요청 때 프롬프트에 이전 맥락을 주입할 수 있게 한다.</p>
 */
@Entity
@Table(
        name = "conversation_messages",
        indexes = @Index(name = "idx_conversation_created", columnList = "conversation_id, created_at")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConversationMessageEntity {

    /** 메시지 작성자 */
    public enum Role {
        USER,
        ASSISTANT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 대화 세션 식별자 */
    @Column(name = "conversation_id", nullable = false)
    private String conversationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    @Column(name = "content", nullable = false, length = 8000)
    private String content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public ConversationMessageEntity(String conversationId, Role role, String content) {
        this.conversationId = conversationId;
        this.role = role;
        this.content = content;
        this.createdAt = Instant.now();
    }
}

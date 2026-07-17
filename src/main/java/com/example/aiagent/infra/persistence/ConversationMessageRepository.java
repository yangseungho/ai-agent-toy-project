package com.example.aiagent.infra.persistence;

import com.example.aiagent.domain.ConversationMessageEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 대화 이력 Repository (PostgreSQL).
 */
public interface ConversationMessageRepository extends JpaRepository<ConversationMessageEntity, Long> {

    /**
     * 특정 대화의 최근 메시지를 최신순으로 조회한다.
     * (Pageable 로 개수를 제한하여 프롬프트가 무한정 커지는 것을 막는다.)
     */
    List<ConversationMessageEntity> findByConversationIdOrderByCreatedAtDesc(String conversationId, Pageable pageable);
}

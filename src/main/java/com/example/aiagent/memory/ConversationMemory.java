package com.example.aiagent.memory;

import com.example.aiagent.config.AgentProperties;
import com.example.aiagent.domain.ConversationMessageEntity;
import com.example.aiagent.infra.persistence.ConversationMessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 대화 이력(Memory) — <b>PostgreSQL 에 실제 영속화</b>한다.
 *
 * <p>Agent 가 멀티턴 대화를 하려면 이전 맥락을 기억해야 한다. 인메모리 Map 으로도
 * 되지만 서버를 재시작하거나 인스턴스를 여러 대 띄우면 즉시 깨진다. 그래서 실전에서는
 * 이렇게 외부 저장소에 남긴다.</p>
 *
 * <p>주의: 이력을 <b>전부</b> 프롬프트에 넣으면 안 된다. 대화가 길어질수록 토큰 비용과
 * 지연이 선형으로 증가하고 결국 컨텍스트 한도를 넘긴다. 그래서 최근 N개만 로드한다
 * ({@code agent.memory.max-history-messages}). 더 긴 대화가 필요하면 요약(summarization)이나
 * 압축(compaction)을 붙이는 것이 다음 단계다.</p>
 */
@Slf4j
@Service
public class ConversationMemory {

    private final ConversationMessageRepository repository;
    private final AgentProperties.Memory config;

    public ConversationMemory(ConversationMessageRepository repository, AgentProperties properties) {
        this.repository = repository;
        this.config = properties.getMemory();
    }

    /**
     * 최근 대화 이력을 시간순(오래된 → 최신)으로 로드한다.
     *
     * @return 이력. 조회 실패 시에도 예외를 던지지 않고 빈 리스트를 반환한다
     *         (기억을 못 할 뿐, 대화 자체는 계속되어야 하므로).
     */
    @Transactional(readOnly = true)
    public List<ChatMessage> load(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return List.of();
        }

        try {
            // 최신순으로 N개를 가져온 뒤 뒤집어서 시간순으로 만든다.
            List<ConversationMessageEntity> recent = repository
                    .findByConversationIdOrderByCreatedAtDesc(
                            conversationId, PageRequest.of(0, config.getMaxHistoryMessages()));

            List<ChatMessage> history = new ArrayList<>();
            for (ConversationMessageEntity entity : recent) {
                ChatMessage.Role role = (entity.getRole() == ConversationMessageEntity.Role.USER)
                        ? ChatMessage.Role.USER
                        : ChatMessage.Role.ASSISTANT;
                history.add(new ChatMessage(role, entity.getContent()));
            }
            Collections.reverse(history);
            return history;

        } catch (DataAccessException e) {
            log.error("[Memory] 대화 이력 조회 실패 conversationId={}", conversationId, e);
            return List.of();
        }
    }

    /**
     * 이번 턴의 질문과 답변을 저장한다.
     *
     * <p>저장 실패가 사용자 응답을 막으면 안 된다. 이미 좋은 답변을 만들었는데
     * 로깅용 저장이 실패했다고 500을 내려주는 것은 최악이다. 그래서 실패를 삼킨다.</p>
     */
    @Transactional
    public void save(String conversationId, String question, String answer) {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }

        try {
            repository.save(new ConversationMessageEntity(
                    conversationId, ConversationMessageEntity.Role.USER, question));
            repository.save(new ConversationMessageEntity(
                    conversationId, ConversationMessageEntity.Role.ASSISTANT, answer));

        } catch (DataAccessException e) {
            log.error("[Memory] 대화 이력 저장 실패 conversationId={}", conversationId, e);
        }
    }
}

package com.example.aiagent.prompt;

import com.example.aiagent.memory.ChatMessage;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM 에 전달할 Prompt.
 *
 * <p>실제 Messages API 의 구조와 1:1로 대응된다.</p>
 * <ul>
 *     <li>{@link #systemInstruction} → system 파라미터 (역할/규칙)</li>
 *     <li>{@link #history} → 이전 대화 턴 (Memory 에서 로드)</li>
 *     <li>{@link #userMessage} → 이번 턴 사용자 메시지 (Tool 로 수집한 근거 + 질문)</li>
 *     <li>{@link #reflectionNote} → Validation 실패 시 Reflection 이 덧붙이는 교정 지시</li>
 * </ul>
 */
@Getter
public class Prompt {

    private final String systemInstruction;
    private final List<ChatMessage> history;
    private final String userMessage;
    private final String reflectionNote;

    public Prompt(String systemInstruction, List<ChatMessage> history, String userMessage, String reflectionNote) {
        this.systemInstruction = systemInstruction;
        this.history = List.copyOf(history);
        this.userMessage = userMessage;
        this.reflectionNote = reflectionNote;
    }

    public boolean hasReflectionNote() {
        return reflectionNote != null && !reflectionNote.isBlank();
    }

    /** 교정 지시만 추가한 새 Prompt 를 만든다 (불변 유지). */
    public Prompt withReflectionNote(String note) {
        return new Prompt(systemInstruction, history, userMessage, note);
    }

    /**
     * 이번 턴에 LLM 으로 보낼 최종 사용자 메시지.
     * Reflection 지시가 있으면 뒤에 덧붙인다.
     */
    public String effectiveUserMessage() {
        if (!hasReflectionNote()) {
            return userMessage;
        }
        return userMessage + "\n\n[이전 답변 교정 지시]\n" + reflectionNote;
    }

    /** 디버깅/로그용 전체 텍스트. */
    public String toText() {
        StringBuilder sb = new StringBuilder();
        sb.append("[SYSTEM]\n").append(systemInstruction).append("\n\n");
        if (!history.isEmpty()) {
            sb.append("[HISTORY]\n");
            for (ChatMessage message : history) {
                sb.append(message.role()).append(": ").append(message.content()).append("\n");
            }
            sb.append("\n");
        }
        sb.append("[USER]\n").append(effectiveUserMessage());
        return sb.toString();
    }

    /** 빈 히스토리 리스트 헬퍼. */
    public static List<ChatMessage> noHistory() {
        return new ArrayList<>();
    }
}

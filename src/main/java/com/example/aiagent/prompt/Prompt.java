package com.example.aiagent.prompt;

import lombok.Getter;

/**
 * LLM 에 전달할 Prompt.
 *
 * <p>Planner 가 모은 정보(Tool 결과)를 사람이 읽을 수 있는 하나의 텍스트로 조립한 것이다.
 * 실제 LLM 이라면 이 텍스트를 입력으로 받아 답변을 생성한다.</p>
 *
 * <p>{@code reflectionNote} 는 Reflection 단계에서 채워진다. Validation 이 실패했을 때
 * "이런 점을 지켜서 다시 답하라"는 교정 지시가 담기며, FakeLLM 은 이 값이 있으면
 * 교정된 답변을 반환한다.</p>
 */
@Getter
public class Prompt {

    /** 시스템 지시문 (역할/규칙) */
    private final String systemInstruction;

    /** 사용자 질문 */
    private final String question;

    /** Tool 들이 수집한 정보를 정리한 컨텍스트 */
    private final String contextInfo;

    /** Reflection 교정 지시 (없으면 null) */
    private final String reflectionNote;

    public Prompt(String systemInstruction, String question, String contextInfo, String reflectionNote) {
        this.systemInstruction = systemInstruction;
        this.question = question;
        this.contextInfo = contextInfo;
        this.reflectionNote = reflectionNote;
    }

    /** Reflection 교정 지시가 포함되어 있는지 여부. */
    public boolean hasReflectionNote() {
        return reflectionNote != null && !reflectionNote.isBlank();
    }

    /** 기존 Prompt 에 Reflection 교정 지시만 추가한 새 Prompt 를 만든다. (불변 객체 유지) */
    public Prompt withReflectionNote(String note) {
        return new Prompt(systemInstruction, question, contextInfo, note);
    }

    /** LLM 입력으로 쓸 최종 텍스트로 합친다. */
    public String toText() {
        StringBuilder sb = new StringBuilder();
        sb.append("[SYSTEM]\n").append(systemInstruction).append("\n\n");
        sb.append("[CONTEXT]\n").append(contextInfo).append("\n\n");
        sb.append("[QUESTION]\n").append(question).append("\n");
        if (hasReflectionNote()) {
            sb.append("\n[REFLECTION]\n").append(reflectionNote).append("\n");
        }
        return sb.toString();
    }
}

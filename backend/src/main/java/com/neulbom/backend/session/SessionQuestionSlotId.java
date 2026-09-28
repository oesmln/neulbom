package com.neulbom.backend.session;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class SessionQuestionSlotId implements Serializable {

    private UUID sessionId;
    private int questionOrder;

    public SessionQuestionSlotId() {
    }

    public SessionQuestionSlotId(UUID sessionId, int questionOrder) {
        this.sessionId = sessionId;
        this.questionOrder = questionOrder;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SessionQuestionSlotId that)) return false;
        return questionOrder == that.questionOrder && Objects.equals(sessionId, that.sessionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId, questionOrder);
    }
}

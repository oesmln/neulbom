package com.neulbom.backend.session.integration;

import java.util.List;

import com.neulbom.backend.analysis.api.QaPair;

public interface ConversationQuestionGenerator {

    String generateNextQuestion(
            List<QaPair> conversation,
            int questionOrder,
            int remainingQuestionCount
    );
}

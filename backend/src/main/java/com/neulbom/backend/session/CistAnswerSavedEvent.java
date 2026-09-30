package com.neulbom.backend.session;

import java.util.UUID;

/** A recorded CIST answer has committed and can be prefetched independently. */
public record CistAnswerSavedEvent(UUID answerId) {
}

package com.neulbom.backend.session;

import java.util.UUID;

/**
 * 세션이 실제로 종료(active → ended)된 뒤 발행된다. 종료 트랜잭션이 커밋된 후에만
 * 소비되어야 하므로 리스너는 {@code @TransactionalEventListener(AFTER_COMMIT)}를 쓴다.
 */
public record SessionEndedEvent(UUID sessionId, UUID userId, String sessionType) {
}

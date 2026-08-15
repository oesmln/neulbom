package com.neulbom.backend.auth.api;

public record EmailAvailabilityResponse(
        String email,
        boolean available
) {
}

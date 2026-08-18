package com.neulbom.backend.guardian;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neulbom.backend.auth.service.TokenHasher;
import com.neulbom.backend.guardian.api.GuardianLinkResponse;
import com.neulbom.backend.guardian.api.InvitationCreateResponse;
import com.neulbom.backend.user.UserEntity;
import com.neulbom.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GuardianIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GuardianAccessService guardianAccessService;

    @Autowired
    private GuardianInvitationRepository invitationRepository;

    @Autowired
    private TokenHasher tokenHasher;

    @Test
    void invitationIsHashedVerifiedOnceAndCreatesActiveScopedLink() throws Exception {
        UserEntity guardian = saveUser("guardian-invite", "guardian");
        UserEntity elder = saveUser("elder-invite", "elder");
        UserEntity secondElder = saveUser("elder-invite-second", "elder");
        Instant issuedAfter = Instant.now();

        String invitationBody = mockMvc.perform(post("/api/v1/guardian/invitations")
                        .with(jwtFor(guardian))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "relation": "딸",
                                  "access_scope": ["screening", "diary"],
                                  "expires_in": 600
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invite_code").value(org.hamcrest.Matchers.matchesRegex("\\d{6}")))
                .andExpect(jsonPath("$.access_scope[0]").value("screening"))
                .andReturn().getResponse().getContentAsString();
        InvitationCreateResponse invitation = objectMapper.readValue(invitationBody, InvitationCreateResponse.class);
        org.assertj.core.api.Assertions.assertThat(invitation.expiresAt())
                .isBetween(issuedAfter.plusSeconds(599), Instant.now().plusSeconds(601));

        mockMvc.perform(post("/api/v1/guardian/invitations/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invite_code\":\"" + invitation.inviteCode() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invitation_id").value(invitation.invitationId().toString()))
                .andExpect(jsonPath("$.requires_consent").value(true));

        mockMvc.perform(post("/api/v1/guardian/invitations/accept")
                        .with(jwtFor(elder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invite_code\":\"" + invitation.inviteCode() + "\",\"consent_agreed\":false}"))
                .andExpect(status().isUnprocessableEntity());

        String linkBody = mockMvc.perform(post("/api/v1/guardian/invitations/accept")
                        .with(jwtFor(elder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invite_code\":\"" + invitation.inviteCode() + "\",\"consent_agreed\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("active"))
                .andExpect(jsonPath("$.consent_required").value(false))
                .andExpect(jsonPath("$.elder_id").value(elder.getId().toString()))
                .andReturn().getResponse().getContentAsString();
        GuardianLinkResponse link = objectMapper.readValue(linkBody, GuardianLinkResponse.class);

        guardianAccessService.requireAccess(guardian.getId(), elder.getId(), "screening");
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> guardianAccessService.requireAccess(guardian.getId(), elder.getId(), "summary"))
                .isInstanceOf(com.neulbom.backend.common.exception.ApiException.class)
                .extracting("status")
                .isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);

        mockMvc.perform(post("/api/v1/guardian/invitations/accept")
                        .with(jwtFor(secondElder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invite_code\":\"" + invitation.inviteCode() + "\",\"consent_agreed\":true}"))
                .andExpect(status().isGone());

        mockMvc.perform(get("/api/v1/guardian/{guardianId}/elders", guardian.getId())
                        .with(jwtFor(guardian)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.elders.length()").value(1))
                .andExpect(jsonPath("$.elders[0].link_id").value(link.linkId().toString()))
                .andExpect(jsonPath("$.elders[0].consent_status").value("agreed"));
    }

    @Test
    void invitationExpiresAfterTenMinutesAndCannotBeVerifiedOrAccepted() throws Exception {
        UserEntity guardian = saveUser("guardian-expired-invite", "guardian");
        UserEntity elder = saveUser("elder-expired-invite", "elder");
        String inviteCode = "654321";
        Instant now = Instant.now();
        GuardianInvitationEntity expiredInvitation = invitationRepository.save(
                new GuardianInvitationEntity(
                        UUID.randomUUID(),
                        guardian.getId(),
                        tokenHasher.hash(inviteCode),
                        "보호자",
                        now.minusSeconds(1),
                        5,
                        now.minusSeconds(601)));

        mockMvc.perform(post("/api/v1/guardian/invitations/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invite_code\":\"" + inviteCode + "\"}"))
                .andExpect(status().isGone());

        org.assertj.core.api.Assertions.assertThat(
                        invitationRepository.findById(expiredInvitation.getId()).orElseThrow().getStatus())
                .isEqualTo(GuardianInvitationEntity.EXPIRED);

        mockMvc.perform(post("/api/v1/guardian/invitations/accept")
                        .with(jwtFor(elder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invite_code\":\"" + inviteCode + "\",\"consent_agreed\":true}"))
                .andExpect(status().isGone());
    }

    @Test
    void directLinkRequiresConsentBeforeActivationAndRejectsIdor() throws Exception {
        UserEntity guardian = saveUser("guardian-direct", "guardian");
        UserEntity otherGuardian = saveUser("guardian-other", "guardian");
        UserEntity elder = saveUser("elder-direct", "elder");

        String pendingBody = mockMvc.perform(post("/api/v1/guardian/link")
                        .with(jwtFor(guardian))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"elder_id\":\"" + elder.getId() + "\",\"access_scope\":[\"summary\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("pending"))
                .andExpect(jsonPath("$.consent_required").value(true))
                .andReturn().getResponse().getContentAsString();
        GuardianLinkResponse pending = objectMapper.readValue(pendingBody, GuardianLinkResponse.class);

        mockMvc.perform(patch("/api/v1/guardian/link/{linkId}", pending.linkId())
                        .with(jwtFor(guardian))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"active\"}"))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(post("/api/v1/consent/{userId}", elder.getId())
                        .with(jwtFor(elder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "consent_type": "guardian_access",
                                  "agreed": true,
                                  "agreed_at": "2026-08-08T10:00:00Z",
                                  "version": "guardian-test-v1"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(patch("/api/v1/guardian/link/{linkId}", pending.linkId())
                        .with(jwtFor(guardian))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"active\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("active"));

        guardianAccessService.requireAccess(guardian.getId(), elder.getId(), "summary");

        mockMvc.perform(get("/api/v1/guardian/{guardianId}/elders", guardian.getId())
                        .with(jwtFor(otherGuardian)))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/guardian/link/{linkId}", pending.linkId())
                        .with(jwtFor(otherGuardian))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"revoked\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/guardian/link/{linkId}", pending.linkId())
                        .with(jwtFor(guardian)))
                .andExpect(status().isNoContent());

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> guardianAccessService.requireAccess(guardian.getId(), elder.getId(), "summary"))
                .isInstanceOf(com.neulbom.backend.common.exception.ApiException.class)
                .extracting("status")
                .isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);
    }

    @Test
    void roleBoundariesRejectWrongRoleOperations() throws Exception {
        UserEntity guardian = saveUser("guardian-role", "guardian");
        UserEntity elder = saveUser("elder-role", "elder");

        mockMvc.perform(post("/api/v1/guardian/invitations")
                        .with(jwtFor(elder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/guardian/invitations/accept")
                        .with(jwtFor(guardian))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invite_code\":\"123456\",\"consent_agreed\":true}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/guardian/link")
                        .with(jwtFor(guardian))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"elder_id\":\"" + guardian.getId() + "\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void invitationVerificationIsRateLimitedByClientIp() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/api/v1/guardian/invitations/verify")
                            .with(request -> {
                                request.setRemoteAddr("198.51.100.10");
                                return request;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"invite_code\":\"123456\"}"))
                    .andExpect(status().isNotFound());
        }

        mockMvc.perform(post("/api/v1/guardian/invitations/verify")
                        .with(request -> {
                            request.setRemoteAddr("198.51.100.10");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invite_code\":\"123456\"}"))
                .andExpect(status().isTooManyRequests());
    }

    private UserEntity saveUser(String prefix, String role) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        return userRepository.save(new UserEntity(
                id,
                prefix + "-" + id + "@example.com",
                null,
                "테스트 " + role,
                role,
                LocalDate.of(1945, 1, 1),
                "80s_plus",
                "female",
                null,
                false,
                now,
                now));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor jwtFor(UserEntity user) {
        return jwt().jwt(jwt -> jwt
                .subject(user.getId().toString())
                .claim("role", user.getRole()));
    }
}

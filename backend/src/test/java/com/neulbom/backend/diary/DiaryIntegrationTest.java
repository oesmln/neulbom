package com.neulbom.backend.diary;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import com.neulbom.backend.analysis.DailySummaryEntity;
import com.neulbom.backend.analysis.DailySummaryRepository;
import com.neulbom.backend.analysis.SessionSummaryEntity;
import com.neulbom.backend.analysis.SessionSummaryRepository;
import com.neulbom.backend.common.id.UuidGenerator;
import com.neulbom.backend.guardian.GuardianLinkEntity;
import com.neulbom.backend.guardian.GuardianLinkRepository;
import com.neulbom.backend.guardian.GuardianLinkScopeEntity;
import com.neulbom.backend.guardian.GuardianLinkScopeRepository;
import com.neulbom.backend.session.SessionEntity;
import com.neulbom.backend.session.SessionRepository;
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
class DiaryIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private SessionSummaryRepository sessionSummaryRepository;
    @Autowired private DailySummaryRepository dailySummaryRepository;
    @Autowired private GuardianLinkRepository guardianLinkRepository;
    @Autowired private GuardianLinkScopeRepository guardianLinkScopeRepository;
    @Autowired private UuidGenerator uuidGenerator;

    @Test
    void diaryCrudGenerationCalendarAndGuardianReactionWorkWithIdorBoundaries() throws Exception {
        UserEntity elder = saveUser("diary-elder", "elder");
        UserEntity guardian = saveUser("diary-guardian", "guardian");
        Instant now = Instant.now();
        SessionEntity session = sessionRepository.save(new SessionEntity(uuidGenerator.generate(), elder.getId(), "emotional_qa", 3, "{}", false, now));
        sessionSummaryRepository.save(new SessionSummaryEntity(uuidGenerator.generate(), session.getId(), elder.getId(), "오늘 산책을 했어요.",
                new BigDecimal("70"), "[]", 1, "completed", now, now));
        DailySummaryEntity dailySummary = dailySummaryRepository.save(new DailySummaryEntity(uuidGenerator.generate(), elder.getId(),
                now.atZone(ZoneId.of("Asia/Seoul")).toLocalDate(), "Asia/Seoul", 1, 1, "completed", "오늘 대화 요약", "[{\"session_id\":\"" + session.getId() + "\"}]", now, now));
        GuardianLinkEntity link = guardianLinkRepository.save(new GuardianLinkEntity(uuidGenerator.generate(), guardian.getId(), elder.getId(),
                "자녀", GuardianLinkEntity.ACTIVE, false, now, now));
        guardianLinkScopeRepository.save(new GuardianLinkScopeEntity(link.getId(), "diary"));
        guardianLinkScopeRepository.save(new GuardianLinkScopeEntity(link.getId(), "activity"));

        String diaryBody = mockMvc.perform(post("/api/v1/diaries/from-session")
                        .with(jwtFor(elder)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"session_id\":\"" + session.getId() + "\",\"user_id\":\"" + elder.getId() + "\",\"mood\":\"happy\",\"mood_level\":4}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source_type").value("session"))
                .andExpect(jsonPath("$.content").value("오늘 산책을 했어요."))
                .andReturn().getResponse().getContentAsString();
        UUID diaryId = UUID.fromString(new com.fasterxml.jackson.databind.ObjectMapper().readTree(diaryBody).get("diary_id").asText());

        mockMvc.perform(get("/api/v1/diaries/{userId}", elder.getId()).with(jwtFor(elder)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.diaries.length()").value(1));
        mockMvc.perform(get("/api/v1/diaries/{diaryId}", diaryId).with(jwtFor(elder)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.diary_id").value(diaryId.toString()));
        mockMvc.perform(patch("/api/v1/diaries/{diaryId}", diaryId).with(jwtFor(elder)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"수정한 일기\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("수정한 일기"));

        mockMvc.perform(post("/api/v1/diaries/{diaryId}/reactions", diaryId).with(jwtFor(guardian)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reaction_type\":\"heart\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.reaction_type").value("heart"));
        mockMvc.perform(get("/api/v1/diaries/{diaryId}/reactions", diaryId).with(jwtFor(elder)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reactions.length()").value(1));
        LocalDate calendarDate = now.atZone(ZoneId.of("Asia/Seoul")).toLocalDate();
        mockMvc.perform(get("/api/v1/calendar/{userId}/activities", elder.getId()).with(jwtFor(guardian))
                        .param("from_date", calendarDate.minusDays(1).toString())
                        .param("to_date", calendarDate.plusDays(1).toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activities").isArray());

        String generationBody = mockMvc.perform(post("/api/v1/diaries/from-daily-summary").with(jwtFor(elder)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"daily_summary_id\":\"" + dailySummary.getId() + "\",\"user_id\":\"" + elder.getId() + "\"}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("completed"))
                .andReturn().getResponse().getContentAsString();
        String generatedDiaryId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(generationBody).get("diary_id").asText();
        mockMvc.perform(get("/api/v1/diaries/{userId}/generation-status", elder.getId()).with(jwtFor(elder))
                        .param("date", dailySummary.getLocalDate().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.diary_id").value(generatedDiaryId));

        mockMvc.perform(delete("/api/v1/diaries/{diaryId}", diaryId).with(jwtFor(guardian)))
                .andExpect(status().isForbidden());
    }

    private UserEntity saveUser(String prefix, String role) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        return userRepository.save(new UserEntity(id, prefix + "-" + id + "@example.com", null, prefix, role,
                LocalDate.of(1945, 1, 1), "80s_plus", "female", null, false, now, now));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor jwtFor(UserEntity user) {
        return jwt().jwt(jwt -> jwt.subject(user.getId().toString()).claim("role", user.getRole()));
    }
}

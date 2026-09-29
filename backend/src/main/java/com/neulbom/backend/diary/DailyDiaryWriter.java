package com.neulbom.backend.diary;

import java.time.LocalDate;
import java.util.List;

import com.neulbom.backend.analysis.api.QaPair;

public interface DailyDiaryWriter {

    boolean isConfigured();

    String write(LocalDate date, List<List<QaPair>> conversations);
}

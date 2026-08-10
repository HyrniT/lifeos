package com.lifeos.habit.web;

import com.lifeos.common.security.UserPrincipal;
import com.lifeos.habit.dto.HabitDtos.AchievementView;
import com.lifeos.habit.dto.HabitDtos.StatsResponse;
import com.lifeos.habit.service.GamificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/gamification")
@Tag(name = "Gamification")
public class GamificationController {

    private final GamificationService gamification;

    public GamificationController(GamificationService gamification) {
        this.gamification = gamification;
    }

    @GetMapping("/stats")
    @Operation(summary = "XP, level, coins, HP and the cross-habit day streak")
    public StatsResponse stats(@AuthenticationPrincipal UserPrincipal me) {
        return StatsResponse.from(gamification.statsFor(me.id()));
    }

    @GetMapping("/achievements")
    @Operation(summary = "Full achievement catalogue with unlock state and progress")
    public List<AchievementView> achievements(@AuthenticationPrincipal UserPrincipal me) {
        return gamification.catalogue(me.id());
    }
}

package com.lifeos.planning.web;

import com.lifeos.common.security.UserPrincipal;
import com.lifeos.planning.dto.PlanningDtos.*;
import com.lifeos.planning.service.FocusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Focus sessions & journal")
public class FocusJournalController {

    private final FocusService focus;

    public FocusJournalController(FocusService focus) {
        this.focus = focus;
    }

    // ================================================================ focus
    @PostMapping("/api/focus/start")
    @Operation(summary = "Start a pomodoro or deep-work block (closes any stale open session)")
    public FocusResponse start(@AuthenticationPrincipal UserPrincipal me,
                               @RequestBody(required = false) StartFocusRequest req) {
        return focus.start(me.id(), req == null ? new StartFocusRequest(null, null, null) : req);
    }

    @PostMapping("/api/focus/{id}/end")
    public FocusResponse end(@AuthenticationPrincipal UserPrincipal me,
                             @PathVariable UUID id,
                             @RequestBody(required = false) EndFocusRequest req) {
        return focus.end(me.id(), id, req);
    }

    @GetMapping("/api/focus/current")
    @Operation(summary = "The session currently running, if any")
    public FocusResponse current(@AuthenticationPrincipal UserPrincipal me) {
        return focus.current(me.id());
    }

    @GetMapping("/api/focus")
    public List<FocusResponse> history(
            @AuthenticationPrincipal UserPrincipal me,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        return focus.history(me.id(), start, end);
    }

    // ============================================================== journal
    @PutMapping("/api/journal")
    @Operation(summary = "Create or replace the entry for a date")
    public JournalResponse upsert(@AuthenticationPrincipal UserPrincipal me,
                                  @Valid @RequestBody JournalRequest req) {
        return focus.upsertJournal(me.id(), req);
    }

    @GetMapping("/api/journal")
    public List<JournalResponse> range(
            @AuthenticationPrincipal UserPrincipal me,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        return focus.journalRange(me.id(), start, end);
    }

    @GetMapping("/api/journal/{date}")
    public JournalResponse on(@AuthenticationPrincipal UserPrincipal me,
                              @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return focus.journalOn(me.id(), date);
    }

    @DeleteMapping("/api/journal/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UserPrincipal me, @PathVariable UUID id) {
        focus.deleteJournal(me.id(), id);
    }
}

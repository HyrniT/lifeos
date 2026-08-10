package com.lifeos.planning.domain;

import com.lifeos.planning.domain.PlanningEnums.SessionType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One pomodoro or deep-work block. Abandoned sessions are kept, flagged incomplete. */
@Entity
@Table(name = "focus_session", schema = "planning", indexes = {
        @Index(name = "idx_focus_user_date", columnList = "user_id,session_date"),
        @Index(name = "idx_focus_task", columnList = "task_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FocusSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "task_id")
    private UUID taskId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private SessionType type = SessionType.POMODORO;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "planned_minutes", nullable = false)
    @Builder.Default
    private int plannedMinutes = 25;

    @Column(name = "actual_minutes", nullable = false)
    @Builder.Default
    private int actualMinutes = 0;

    @Column(nullable = false)
    @Builder.Default
    private boolean completed = false;

    /** Self-reported 1-5; the input for the "when do I focus best" chart. */
    @Column(name = "focus_score")
    private Integer focusScore;

    @Column(length = 500)
    private String note;
}

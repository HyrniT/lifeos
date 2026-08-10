package com.lifeos.planning.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One reflection per day. The mood and energy numbers feed the cross-domain charts. */
@Entity
@Table(name = "journal_entry", schema = "planning", uniqueConstraints = {
        @UniqueConstraint(name = "uk_journal_user_date", columnNames = {"user_id", "entry_date"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JournalEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    /** 1 (rough) … 5 (great). */
    @Column
    private Integer mood;

    /** 1 (drained) … 5 (energised). */
    @Column
    private Integer energy;

    @Column(length = 1000)
    private String highlights;

    @Column(length = 1000)
    private String gratitude;

    @Column(length = 4000)
    private String notes;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}

package com.lifeos.planning.repo;

import com.lifeos.planning.domain.JournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JournalRepository extends JpaRepository<JournalEntry, UUID> {

    Optional<JournalEntry> findByUserIdAndEntryDate(UUID userId, LocalDate entryDate);

    List<JournalEntry> findByUserIdAndEntryDateBetweenOrderByEntryDateDesc(
            UUID userId, LocalDate from, LocalDate to);

    Optional<JournalEntry> findByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);
}

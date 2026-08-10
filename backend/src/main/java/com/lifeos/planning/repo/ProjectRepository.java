package com.lifeos.planning.repo;

import com.lifeos.planning.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    List<Project> findByUserIdOrderBySortOrderAscNameAsc(UUID userId);

    Optional<Project> findByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);
}

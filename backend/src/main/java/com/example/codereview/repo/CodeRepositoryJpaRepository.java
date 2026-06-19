package com.example.codereview.repo;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CodeRepositoryJpaRepository extends JpaRepository<CodeRepositoryEntity, Long> {

    Optional<CodeRepositoryEntity> findByProjectId(Long projectId);

    List<CodeRepositoryEntity> findByRepoUrlContaining(String fragment);
}

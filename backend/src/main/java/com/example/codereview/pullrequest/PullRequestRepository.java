package com.example.codereview.pullrequest;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PullRequestRepository extends JpaRepository<PullRequestEntity, Long> {

    List<PullRequestEntity> findByProjectIdOrderByUpdatedAtDesc(Long projectId);

    Optional<PullRequestEntity> findByRepositoryIdAndPrNumber(Long repositoryId, Integer prNumber);

    void deleteByProjectId(Long projectId);
}

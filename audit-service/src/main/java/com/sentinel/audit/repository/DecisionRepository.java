package com.sentinel.audit.repository;

import com.sentinel.audit.model.Decision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface DecisionRepository extends JpaRepository<Decision, String> {
    List<Decision> findByUserIdOrderByCreatedAtDesc(String userId);
//    List<Decision> findByDecisionOrderByCreatedAtDesc(String decision);
    List<Decision> findTop50ByDecisionOrderByCreatedAtDesc(String decision);

    //for drift controller
    @Query("SELECT d FROM Decision d WHERE d.createdAt >= :since")
    List<Decision> findRecentDecisions(@Param("since") Instant since);
}
package com.sentinel.audit.repository;

import com.sentinel.audit.model.Decision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DecisionRepository extends JpaRepository<Decision, String> {
    List<Decision> findByUserIdOrderByCreatedAtDesc(String userId);
}
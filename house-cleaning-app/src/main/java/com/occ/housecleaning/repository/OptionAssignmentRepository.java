package com.occ.housecleaning.repository;

import com.occ.housecleaning.model.OptionAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OptionAssignmentRepository extends JpaRepository<OptionAssignment, Long> {
    List<OptionAssignment> findByExerciseIdOrderByIdAsc(String exerciseId);
}

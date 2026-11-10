package com.occ.housecleaning.repository;

import com.occ.housecleaning.model.OptionExercise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OptionExerciseRepository extends JpaRepository<OptionExercise, String> {
}

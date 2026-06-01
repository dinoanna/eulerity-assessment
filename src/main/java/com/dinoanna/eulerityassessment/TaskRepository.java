package com.dinoanna.eulerityassessment;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, Long> {
    long countByPriority(Priority priority);

    List<Task> findByPriority(Priority priority);
}
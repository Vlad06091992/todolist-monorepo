package io.roadmap.todolistmonorepo.repositories;

import io.roadmap.todolistmonorepo.entities.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

@Repository
public interface TasksRepository extends JpaRepository<Task, UUID> {
//    Optional<Task> findByUsername(String username);
//    List<Task> findByIsFinished(boolean isFinished);
    List<Task> findAll ();

}

package io.roadmap.todolistmonorepo.repositories;

import io.roadmap.todolistmonorepo.entities.Task;
import io.roadmap.todolistmonorepo.entities.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

@Repository
public interface TasksRepository extends JpaRepository<Task, UUID> {
    @EntityGraph(attributePaths = {"user"})
    List<Task> findAllByUser_Id(UUID userId);

    @EntityGraph(attributePaths = {"user"})
    Optional<Task> getTaskById(UUID id);

    @Modifying
    @Query("DELETE FROM Task t WHERE t.id = :taskId AND t.user.id = :userId")
    @Transactional
    int deleteTaskByIdAndUser(@Param("taskId") UUID taskId, @Param("userId") UUID userId);

    @Modifying
    @Query("UPDATE Task t SET t.isFinished = :finished WHERE t.id = :taskId AND t.user.id = :userId")
    @Transactional
    int updateTaskByIdAndUser(
            @Param("taskId") UUID taskId,
            @Param("userId") UUID userId,
            @Param("finished") boolean finished);

}

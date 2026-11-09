package io.roadmap.todolistmonorepo.repositories;

import io.roadmap.todolistmonorepo.dto.UserSummary;
import io.roadmap.todolistmonorepo.entities.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UsersRepository extends JpaRepository<User, UUID> {
    Optional<User> findByLogin(String login);
//    List<Task> findByIsFinished(boolean isFinished);
//@EntityGraph(attributePaths = {"tasks"})
//    List<User> findAll ();

@Query("SELECT DISTINCT u.id, u.login, STRING_AGG(t.description, ', ') as summary FROM User u JOIN Task t on u.id = t.user.id and t.isFinished = true" )
List<User> findAll ();

    @Query(value = """
    SELECT u.id, u.login, u.email, STRING_AGG(t.description, ', ') AS summary
    FROM users u
    JOIN tasks t ON u.id = t.user_id AND t.is_finished = true
    WHERE t.updated_at >= :fromDate
    GROUP BY u.id, u.login
    """, nativeQuery = true)
    List<UserSummary> findAllSummaries(@Param("fromDate") LocalDateTime fromDate);
}

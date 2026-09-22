package io.roadmap.todolistmonorepo.repositories;

import io.roadmap.todolistmonorepo.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UsersRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsername(String username);
//    List<Task> findByIsFinished(boolean isFinished);
    List<User> findAll ();

}

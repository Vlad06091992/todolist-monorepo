package io.roadmap.tasktracker.emailsender.repositories;

import io.roadmap.tasktracker.emailsender.entities.EventOutbox;
import io.roadmap.tasktracker.emailsender.entities.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EventOutboxRepository extends JpaRepository<EventOutbox, UUID> {

    List<EventOutbox> findByStatus(OutboxStatus status);
}

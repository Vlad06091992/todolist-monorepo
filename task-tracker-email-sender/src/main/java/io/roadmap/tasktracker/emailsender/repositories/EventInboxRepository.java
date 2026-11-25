package io.roadmap.tasktracker.emailsender.repositories;

import io.roadmap.tasktracker.emailsender.entities.EventInbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EventInboxRepository extends JpaRepository<EventInbox, UUID> {
    Optional<EventInbox> findByEventId(UUID eventId);
}


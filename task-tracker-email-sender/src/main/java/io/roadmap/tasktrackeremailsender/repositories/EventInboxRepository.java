package io.roadmap.tasktrackeremailsender.repositories;

import io.roadmap.tasktrackeremailsender.entities.EventInbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EventInboxRepository extends JpaRepository<EventInbox, UUID> {
    Optional<EventInbox> findByEventId(UUID eventId);
}


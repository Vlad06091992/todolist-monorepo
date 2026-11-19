package io.roadmap.tasktrackeremailsender.repositories;

import io.roadmap.tasktrackeremailsender.entities.EventOutbox;
import io.roadmap.tasktrackeremailsender.entities.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EventOutboxRepository extends JpaRepository<EventOutbox, UUID> {

    List<EventOutbox> findByStatus(OutboxStatus status);
}

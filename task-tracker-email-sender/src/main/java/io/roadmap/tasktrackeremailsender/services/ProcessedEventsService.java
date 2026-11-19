package io.roadmap.tasktrackeremailsender.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.roadmap.tasktrackeremailsender.dto.KafkaCreateUserMessage;
import io.roadmap.tasktrackeremailsender.entities.EventOutbox;
import io.roadmap.tasktrackeremailsender.entities.EventInbox;
import io.roadmap.tasktrackeremailsender.entities.OutboxEventType;
import io.roadmap.tasktrackeremailsender.repositories.EventOutboxRepository;
import io.roadmap.tasktrackeremailsender.repositories.EventInboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class ProcessedEventsService {

    EventOutboxRepository eventOutboxRepository;
    EventInboxRepository eventInboxRepository;
    ObjectMapper objectMapper = new ObjectMapper();
    private final Logger LOG = LoggerFactory.getLogger(ProcessedEventsService.class.getName());


    public ProcessedEventsService(EventOutboxRepository eventOutboxRepository, EventInboxRepository eventInboxRepository) {
        this.eventOutboxRepository = eventOutboxRepository;
        this.eventInboxRepository = eventInboxRepository;
    }


    @Transactional
    public void registerEvent(KafkaCreateUserMessage message) {

        if(message.eventId() == null){
            LOG.error("event id required");
            return;
        }

        Optional<EventInbox> processedEvent = eventInboxRepository.findByEventId(UUID.fromString(message.eventId()));


        if(processedEvent.isPresent()){
            LOG.error("event already processed {}",message.eventId() );
            return;
        }

        Map<String,Object> data = objectMapper.convertValue(message, Map.class);

//        // регаем инбокс сущность
        EventInbox eventInbox = new EventInbox();
        eventInbox.setEventId(UUID.fromString(message.eventId()));
        eventInbox.setProcessedAt(LocalDateTime.now());
       EventInbox savedInbox = eventInboxRepository.save(eventInbox);
        //
//        // регаем инбокс сущность
        EventOutbox eventOutbox = new EventOutbox();
        eventOutbox.setEventInbox(savedInbox);
        eventOutbox.setType(OutboxEventType.SEND_EMAIL);
        eventOutbox.setPayload(data);
        eventOutboxRepository.save(eventOutbox);

        LOG.info("success");
    }
}

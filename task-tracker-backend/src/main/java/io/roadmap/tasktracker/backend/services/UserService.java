package io.roadmap.tasktracker.backend.services;

import io.roadmap.tasktracker.backend.dto.UserRegisteredEvent;
import io.roadmap.tasktracker.backend.entities.User;
import io.roadmap.tasktracker.backend.entities.outbox.EventOutbox;
import io.roadmap.tasktracker.backend.entities.outbox.OutboxEventType;
import io.roadmap.tasktracker.backend.exceptions.UserAlreadyExistsException;
import io.roadmap.tasktracker.backend.repositories.EventOutboxRepository;
import io.roadmap.tasktracker.backend.repositories.UsersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {
    private final Scheduler dbScheduler;
    private final UsersRepository usersRepository;
    private final EventOutboxRepository eventOutboxRepository;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    public Mono<User> findByLoginOrEmail(String login, String email) {
        return Mono
                .fromCallable(() -> usersRepository.findByLoginOrEmail(login, email))
                .flatMap(Mono::justOrEmpty)
                .subscribeOn(dbScheduler);
    }

    public Mono<User> findByLogin(String login) {
        return Mono
                .fromCallable(() -> usersRepository.findByLogin(login))
                .flatMap(Mono::justOrEmpty)
                .subscribeOn(dbScheduler);
    }

    public Mono<User> registerUser(User user) {
        return findByLoginOrEmail(user.getLogin(), user.getEmail())
                .flatMap(u -> Mono.<User>error(new UserAlreadyExistsException()))
                .switchIfEmpty(persistUserWithOutboxEvent(user))
                .subscribeOn(dbScheduler);
    }

    private Mono<User> persistUserWithOutboxEvent(User user) {
        return Mono.fromCallable(() -> transactionTemplate.execute(status -> {
            // транзакционно делаем сохраняем eventOutbox и юзера
            EventOutbox eventOutbox = new EventOutbox();
            UUID id = UUID.randomUUID();
            String eventId = id.toString();
            eventOutbox.setId(id);
            eventOutbox.setType(OutboxEventType.USER_REGISTERED);
            UserRegisteredEvent message = new UserRegisteredEvent(user.getLogin(), user.getEmail(), eventId);
            Map<String, Object> payload = objectMapper.convertValue(message, Map.class);
            eventOutbox.setPayload(payload);
            eventOutboxRepository.save(eventOutbox);
            return usersRepository.save(user);
        }));
    }
}
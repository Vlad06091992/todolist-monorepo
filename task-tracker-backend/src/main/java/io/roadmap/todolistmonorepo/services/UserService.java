package io.roadmap.todolistmonorepo.services;

import io.roadmap.todolistmonorepo.dto.KafkaCreateUserMessage;
import io.roadmap.todolistmonorepo.entities.User;
import io.roadmap.todolistmonorepo.entities.outbox.EventOutbox;
import io.roadmap.todolistmonorepo.entities.outbox.OutboxEventType;
import io.roadmap.todolistmonorepo.exceptions.UserAlreadyExistException;
import io.roadmap.todolistmonorepo.repositories.EventOutboxRepository;
import io.roadmap.todolistmonorepo.repositories.UsersRepository;
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
    private final ObjectMapper om;

    public Mono<User> findByLoginAndEmail(String username, String email) {
        return Mono
                .fromCallable(() -> usersRepository.findByLoginOrEmail(username, email))
                .flatMap(Mono::justOrEmpty)
                .subscribeOn(dbScheduler);
    }

    public Mono<User> findByLogin(String username) {
        return Mono
                .fromCallable(() -> usersRepository.findByLogin(username))
                .flatMap(Mono::justOrEmpty)
                .subscribeOn(dbScheduler);
    }

    public Mono<User> createUserWithSendingEmail(User user) {
        return findByLoginAndEmail(user.getLogin(), user.getEmail())
                .flatMap(u -> Mono.<User>error(new UserAlreadyExistException()))
                .switchIfEmpty(createAndSend(user))
                .subscribeOn(dbScheduler);
    }

    private Mono<User> createAndSend(User user) {
        return Mono.fromCallable(() -> transactionTemplate.execute(status -> {
            // транзакционно делаем сохраняем eventOutbox и юзера
            EventOutbox eventOutbox = new EventOutbox();
            UUID id = UUID.randomUUID();
            String stringId = id.toString();
            eventOutbox.setId(id);
            eventOutbox.setType(OutboxEventType.USER_REGISTERED);
            KafkaCreateUserMessage message = new KafkaCreateUserMessage(user.getLogin(), user.getEmail(), stringId);
            Map<String, Object> payload = om.convertValue(message, Map.class);
            eventOutbox.setPayload(payload);
            eventOutboxRepository.save(eventOutbox);
            return usersRepository.save(user);
        }));
    }
}
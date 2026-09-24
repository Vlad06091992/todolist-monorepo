package io.roadmap.todolistmonorepo.services;

import io.roadmap.todolistmonorepo.dto.UserCreateRequest;
import io.roadmap.todolistmonorepo.entities.User;
import io.roadmap.todolistmonorepo.exceptions.UserAlreadyExistException;
import io.roadmap.todolistmonorepo.repositories.UsersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

@Service
@RequiredArgsConstructor
public class UserService {

    private final Scheduler dbScheduler;
    private final UsersRepository usersRepository;

    public Mono<User> findByLogin(String username) {
        return Mono
                .fromCallable(() -> usersRepository.findByLogin(username))
                .flatMap(Mono::justOrEmpty)
                .subscribeOn(dbScheduler);
    }

    public Mono<User> save(User user) {
        return findByLogin(user.getLogin())
                .flatMap(u -> {
                    System.out.println(u);
                    return Mono.<User>error(new UserAlreadyExistException());
                })
                .switchIfEmpty(Mono.fromCallable(() -> usersRepository.save(user)))
                .subscribeOn(dbScheduler);

    }
}
package io.roadmap.todolistmonorepo.services;
import io.roadmap.todolistmonorepo.entities.User;
import io.roadmap.todolistmonorepo.repositories.UsersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final Scheduler dbScheduler;
    private final UsersRepository usersRepository;

    public Mono<User> findByUsername(String username) {
        return Mono
                .fromCallable(() -> usersRepository.findByUsername(username))
                .flatMap(Mono::justOrEmpty)
                .subscribeOn(dbScheduler);
    }

    public Mono<User> save(User user) {

        Mono<User> u = findByUsername(user.getUsername());


        return Mono
                .defer()
                .fromCallable(() -> {})
                .fromCallable(() -> {

                    user.setPassword(user.getPassword());
                    return usersRepository.save(user);
                })
                .subscribeOn(dbScheduler);


    }
}
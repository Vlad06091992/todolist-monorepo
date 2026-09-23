package io.roadmap.todolistmonorepo.services;
import io.roadmap.todolistmonorepo.dto.UserCreateRequest;
import io.roadmap.todolistmonorepo.entities.User;
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

    //TODO пофикксить Object
    public Mono<Object> save(UserCreateRequest userDTO) {




        return findByLogin(userDTO.login())
                .flatMap(u -> {
                    System.out.println(u);
                    return Mono.error(new RuntimeException("User already exist"));
                })
                .switchIfEmpty(Mono.fromCallable(() -> {

                    User user = new User();
                    user.setPassword(userDTO.password());
                    user.setLogin(userDTO.login());
                    user.setEmail(user.getEmail());

                  User res =  usersRepository.save(user);
                  return Mono.just(res);

//                    return Mono.just(usersRepository.save(user));
//                    return Mono.just(usersRepository.save(user));
                }))
                .doOnError((d)->{
                    System.out.println("ERROR!!!!!");
                })
                .subscribeOn(dbScheduler);

    }
}
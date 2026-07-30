package io.roadmap.todolistmonorepo.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Configuration
public class SchedulerConfig {

    @Bean(destroyMethod = "dispose")
    public Scheduler dbScheduler(
            @Value("${spring.datasource.hikari.maximum-pool-size}") int poolSize) {

        return Schedulers.newBoundedElastic(
                poolSize,   // потоков ровно столько же, сколько соединений
                500,        // предел очереди: при перегрузке быстрый отказ, а не рост памяти
                "db");      // префикс имени потока: db-1, db-2, ...
    }
}

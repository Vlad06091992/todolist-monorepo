package io.roadmap.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@Component
public class ScheduledTasks {
    TasksService tasksService;
    SummariesService summariesService;

    private static final Logger log = LoggerFactory.getLogger(ScheduledTasks.class);

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");

    public ScheduledTasks(TasksService tasksService, SummariesService summariesService) {
        this.tasksService = tasksService;
        this.summariesService = summariesService;
    }

    @Scheduled(fixedRate = 5000)
    public void reportCurrentTime() {
        log.info("The time is now {}", dateFormat.format(new Date()));
    }


    @Scheduled(fixedRate = 30000)
    public void reauest() {
        log.info("init reports");
//        TODO реальное время(таски за сутки)
//        Flux<SummaryResponse> res = tasksService.sendRequest( "2026-11-02T14:30");
     Mono<String> k = summariesService.send("2026-11-02T14:30");

             k.map(e -> {
                 System.out.println(e);
                 return e;
             }).subscribe()

             ;
    }
}
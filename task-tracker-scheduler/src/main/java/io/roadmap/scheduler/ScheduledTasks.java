package io.roadmap.scheduler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeoutException;

@Component
public class ScheduledTasks {
    TasksService tasksService;
    SummariesService summariesService;
    protected int count;
    protected Long usersForReportCount;
    protected boolean isPreparedJobFailed = false;
    protected boolean isNeedGenerateReports = true;

    private int pageSize = 20;
    private int pagesCount;
    private int currentPage;

    private static final Logger log = LoggerFactory.getLogger(ScheduledTasks.class);

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");

    public ScheduledTasks(TasksService tasksService, SummariesService summariesService) {
        this.tasksService = tasksService;
        this.summariesService = summariesService;
    }

    private void reportsPreprocessor(Long usersCount) {
        isPreparedJobFailed = false;
        isNeedGenerateReports = false;

        if (usersCount == null) {
            log.error("reportsPreprocessor: usersCount is null");
            isPreparedJobFailed = true;
            return;
        }
        if (usersCount == 0) {
            log.info("report not need, users for report: 0");
            return;
        }
        isNeedGenerateReports = true;
        pagesCount = (int) Math.ceil((double) usersCount / pageSize);
        currentPage = 1;
    }


    // джоба за 5 минут до основной, распределяет пагинацию
//    @Scheduled(cron = "0 55 12 * * *")
//    @Scheduled(fixedRate = 5000)
    public void preparatoryJob() {
        log.info("preparatoryJob");
        tasksService.getCount("2024-11-02T14:30").doOnError((f)-> {
            System.out.println("");
//            return Mono.empty();
        }
//        ).subscribe(this::reportsPreprocessor);
        ).subscribe((d)->{
            System.out.println("3");
            reportsPreprocessor(d);
        },
                (d)->{
                    System.out.println("3");
                    reportsPreprocessor(null);
                }         );
        log.info("preparatoryJob finished");
    }

    //
//
    // основная джоба 20 запусков в течение часа 13:00–13:57, каждые 3 минуты
//    @Scheduled(cron = "0 0/3 13 * * *")
    public void mainJob() {
        if (isPreparedJobFailed) {
            log.error("isPreparedJobFailed, mainJob not started {}", dateFormat.format(new Date()));
            return;
        }

        if (currentPage > pagesCount){
            log.info("mainJob finished {}", dateFormat.format(new Date()));
            return;
        }

        log.info("Main job: {}", dateFormat.format(new Date()));
        summariesService.generateReportsForUsersByPageAndDate("2026-11-02T14:30", currentPage, pageSize).timeout(Duration.ofSeconds(30))
                .retryWhen(
                        Retry.backoff(3, Duration.ofSeconds(5))
                                .maxBackoff(Duration.ofSeconds(20))
                                .filter(t -> t instanceof TimeoutException)
                )
                .doOnError((throwable) -> {
                    System.out.println("timeout error!!!");
                    System.out.println(throwable.toString());
                })
                .then(Mono.fromCallable(() -> currentPage++))
                .subscribe();
    }
}
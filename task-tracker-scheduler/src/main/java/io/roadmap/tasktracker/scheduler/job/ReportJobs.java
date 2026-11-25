package io.roadmap.tasktracker.scheduler.job;

import org.slf4j.Logger;
import io.roadmap.tasktracker.scheduler.client.BackendTaskClient;
import io.roadmap.tasktracker.scheduler.report.ReportGenerationService;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.concurrent.TimeoutException;

@Component
public class ReportJobs {
    BackendTaskClient backendTaskClient;
    ReportGenerationService reportGenerationService;
    protected boolean preparationFailed = false;
    protected boolean shouldGenerateReports = true;

    private int pageSize = 20;
    private int totalPages;
    private int currentPage;

    private static final Logger log = LoggerFactory.getLogger(ReportJobs.class);

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");

    public ReportJobs(BackendTaskClient backendTaskClient, ReportGenerationService reportGenerationService) {
        this.backendTaskClient = backendTaskClient;
        this.reportGenerationService = reportGenerationService;
    }

    private void initPagination(Long usersCount) {
        preparationFailed = false;
        shouldGenerateReports = false;

        if (usersCount == null) {
            log.error("initPagination: usersCount is null");
            preparationFailed = true;
            return;
        }
        if (usersCount == 0) {
            log.info("report not need, users for report: 0");
            return;
        }
        shouldGenerateReports = true;
        totalPages = (int) Math.ceil((double) usersCount / pageSize);
        currentPage = 1;
    }


    // джоба за 5 минут до основной, распределяет пагинацию
//    @Scheduled(cron = "0 55 12 * * *")
    @Scheduled(fixedRate = 5000)
    public void planReportBatches() {
        log.info("planReportBatches");
        backendTaskClient.getCount("2024-11-02T14:30").subscribe(
                this::initPagination,
                (e) -> {
                    log.error("planReportBatches: failed to fetch users count", e);
                    initPagination(null);
                }
        );
        log.info("planReportBatches finished");
    }

    // основная джоба 20 запусков в течение часа 13:00–13:57, каждые 3 минуты
//    @Scheduled(cron = "0 0/3 13 * * *")
    @Scheduled(fixedRate = 20000)
    public void generateNextReportBatch() {
        planReportBatches();
        if (preparationFailed) {
            log.error("preparationFailed, generateNextReportBatch not started {}", dateFormat.format(new Date()));
            return;
        }

        if (currentPage > totalPages){
            log.info("generateNextReportBatch finished {}", dateFormat.format(new Date()));
            return;
        }

        log.info("Main job: {}", dateFormat.format(new Date()));
        reportGenerationService.generateReportsForPage("2026-11-02T14:30", currentPage, pageSize).timeout(Duration.ofSeconds(30))
                .retryWhen(
                        Retry.backoff(3, Duration.ofSeconds(5))
                                .maxBackoff(Duration.ofSeconds(20))
                                .filter(t -> t instanceof TimeoutException)
                )
                .doOnError((throwable) ->
                        log.error("generateNextReportBatch: report generation failed for page {}", currentPage, throwable)
                )
                .then(Mono.fromCallable(() -> currentPage++))
                .subscribe();
    }
}
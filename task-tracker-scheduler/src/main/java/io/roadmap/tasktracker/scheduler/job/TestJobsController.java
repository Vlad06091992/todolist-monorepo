package io.roadmap.tasktracker.scheduler.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/test/jobs")
@RequiredArgsConstructor
public class TestJobsController {

    private final ReportJobs jobs;

    private static final String DEFAULT_DATE = "2026-11-02T14:30";

    /** Только подготовка: посчитать totalPages и выставить currentPage=1 */
    @PostMapping("/prep")
    public Mono<String> prep(@RequestParam(required = false) String date) {
        return Mono.fromRunnable(jobs::planReportBatches);
    }

    /** Один шаг generateNextReportBatch (одна страница) */
    @PostMapping("/main")
    public Mono<Map<String, Object>> main(@RequestParam(required = false) String date) {
        return Mono.fromRunnable(jobs::generateNextReportBatch);
    }

}
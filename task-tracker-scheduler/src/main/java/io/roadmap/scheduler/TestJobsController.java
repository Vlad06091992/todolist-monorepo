package io.roadmap.scheduler;

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

    private final ScheduledTasks jobs;

    private static final String DEFAULT_DATE = "2026-11-02T14:30";

//    /** Сброс состояния перед прогоном */
//    @PostMapping("/reset")
//    public Map<String, Object> reset() {
//        jobs.reset();
//        return statusMap();
//    }

    /** Только подготовка: посчитать pagesCount и выставить currentPage=1 */
    @PostMapping("/prep")
    public Mono<String> prep(@RequestParam(required = false) String date) {
        return Mono.fromRunnable(jobs::preparatoryJob);
//
//       jobs.preparatoryJob();
//        return null;
    }

    /** Один шаг mainJob (одна страница) */
    @PostMapping("/main")
    public Mono<Map<String, Object>> main(@RequestParam(required = false) String date) {
        return Mono.fromRunnable(jobs::mainJob);
    }

}
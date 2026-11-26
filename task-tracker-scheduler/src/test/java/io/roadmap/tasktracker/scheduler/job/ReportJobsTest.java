package io.roadmap.tasktracker.scheduler.job;

import io.roadmap.tasktracker.scheduler.client.BackendTaskClient;
import io.roadmap.tasktracker.scheduler.report.ReportGenerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link ReportJobs} — no Spring context, no infrastructure.
 * <p>
 * Both collaborators are Mockito mocks returning immediate Monos, so the
 * fire-and-forget {@code subscribe(...)} calls inside the jobs settle their
 * state SYNCHRONOUSLY: by the time {@code planReportBatches()} /
 * {@code generateNextReportBatch()} return, the protected fields are already
 * updated and can be asserted directly (the test lives in the same package
 * precisely to read those protected fields).
 * <p>
 * The hardcoded request dates are pinned exactly as they appear in
 * {@code ReportJobs}: {@code "2024-11-02T14:30"} for the users-count probe and
 * {@code "2026-11-02T14:30"} for report generation. The timeout/retry-backoff
 * path is intentionally NOT tested (real 5s/10s/20s backoff would make the
 * suite slow and non-deterministic).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReportJobs pagination state machine (mock-based, synchronous)")
class ReportJobsTest {

    private static final String COUNT_DATE = "2024-11-02T14:30";
    private static final String GENERATION_DATE = "2026-11-02T14:30";
    private static final int PAGE_SIZE = 20;

    @Mock
    private BackendTaskClient backendTaskClient;

    @Mock
    private ReportGenerationService reportGenerationService;

    private ReportJobs reportJobs;

    @BeforeEach
    void setUp() {
        reportJobs = new ReportJobs(backendTaskClient, reportGenerationService);
    }

    @Test
    @DisplayName("planReportBatches: positive user count enables report generation and pins the hardcoded count date")
    void planReportBatches_positiveCount_enablesGeneration() {
        // Five users -> shouldGenerateReports flips true, preparation considered OK.
        when(backendTaskClient.getCount(anyString())).thenReturn(Mono.just(5L));

        reportJobs.planReportBatches();

        // Immediate Mono: state settled synchronously before this assertion runs.
        assertThat(reportJobs.preparationFailed).isFalse();
        assertThat(reportJobs.shouldGenerateReports).isTrue();
        // Pin the hardcoded date passed to the backend count endpoint.
        verify(backendTaskClient).getCount(COUNT_DATE);
    }

    @Test
    @DisplayName("planReportBatches: getCount error marks preparation as failed and disables generation")
    void planReportBatches_countError_marksPreparationFailed() {
        // The error consumer routes to initPagination(null) -> preparationFailed=true.
        when(backendTaskClient.getCount(anyString()))
                .thenReturn(Mono.error(new RuntimeException("backend down")));

        reportJobs.planReportBatches();

        assertThat(reportJobs.preparationFailed).isTrue();
        assertThat(reportJobs.shouldGenerateReports).isFalse();
    }

    @Test
    @DisplayName("planReportBatches: zero users keeps preparation healthy but disables generation")
    void planReportBatches_zeroCount_disablesGeneration() {
        // 0 users: no failure, but also nothing to generate.
        when(backendTaskClient.getCount(anyString())).thenReturn(Mono.just(0L));

        reportJobs.planReportBatches();

        assertThat(reportJobs.preparationFailed).isFalse();
        assertThat(reportJobs.shouldGenerateReports).isFalse();
    }

    @Test
    @DisplayName("generateNextReportBatch: after successful prep the first tick generates page 1 with the hardcoded generation date and pageSize 20")
    void generateNextReportBatch_firstTick_generatesFirstPage() {
        // 5 users -> totalPages=ceil(5/20)=1, currentPage=1, so the first tick generates page 1.
        when(backendTaskClient.getCount(anyString())).thenReturn(Mono.just(5L));
        // Mono.empty() only: the timeout/retry-backoff path is deliberately not exercised.
        when(reportGenerationService.generateReportsForPage(anyString(), anyInt(), anyInt()))
                .thenReturn(Mono.empty());

        // planReportBatches runs independently via @Scheduled (it is NOT invoked inside
        // generateNextReportBatch — the internal call exists only for local debugging),
        // so the test arranges the pagination state by calling it explicitly, exactly
        // like the @Scheduled pairing would in production.
        reportJobs.planReportBatches();
        reportJobs.generateNextReportBatch();

        // Capture the exact arguments handed to the generation service.
        ArgumentCaptor<String> dateCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> pageCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> pageSizeCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(reportGenerationService, times(1))
                .generateReportsForPage(dateCaptor.capture(), pageCaptor.capture(), pageSizeCaptor.capture());

        // Pin the second hardcoded date and the private pageSize=20 (read indirectly).
        assertThat(dateCaptor.getValue()).isEqualTo(GENERATION_DATE);
        assertThat(pageCaptor.getValue()).isEqualTo(1);
        assertThat(pageSizeCaptor.getValue()).isEqualTo(PAGE_SIZE);
    }

    @Test
    @DisplayName("generateNextReportBatch: pages advance across ticks (1, then 2) and generation stops when exhausted")
    void generateNextReportBatch_pagesAdvanceAcrossTicksAndStopWhenExhausted() {
        // 40 users -> totalPages=ceil(40/20)=2: tick 1 generates page 1, tick 2 page 2,
        // tick 3 short-circuits on currentPage(3) > totalPages(2).
        when(backendTaskClient.getCount(anyString())).thenReturn(Mono.just(40L));
        when(reportGenerationService.generateReportsForPage(anyString(), anyInt(), anyInt()))
                .thenReturn(Mono.empty());

        reportJobs.planReportBatches();
        reportJobs.generateNextReportBatch();
        reportJobs.generateNextReportBatch();
        reportJobs.generateNextReportBatch();

        ArgumentCaptor<String> dateCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> pageCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> pageSizeCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(reportGenerationService, times(2))
                .generateReportsForPage(dateCaptor.capture(), pageCaptor.capture(), pageSizeCaptor.capture());

        // Pagination advances across ticks: exactly pages 1 and 2, then the bounds
        // check short-circuits the third tick (currentPage incremented after each
        // successful generation; no pagination reset — planReportBatches is independent).
        assertThat(dateCaptor.getAllValues()).containsExactly(GENERATION_DATE, GENERATION_DATE);
        assertThat(pageCaptor.getAllValues()).containsExactly(1, 2);
        assertThat(pageSizeCaptor.getAllValues()).containsExactly(PAGE_SIZE, PAGE_SIZE);
    }

    @Test
    @DisplayName("generateNextReportBatch: a tick before any successful preparation generates page 0 (job-ordering dependency pin)")
    void generateNextReportBatch_beforePreparation_generatesPageZero() {
        // Initialization-gap pin: fresh instance defaults are preparationFailed=false,
        // shouldGenerateReports=true, currentPage=0, totalPages=0 — so a tick that fires
        // before planReportBatches has ever succeeded proceeds with page 0 (which the
        // backend would reject: page must be >= 1). The main job depends on the prep
        // job having completed first; no getCount interaction proves the jobs are
        // fully independent now (planReportBatches is not called internally anymore).
        when(reportGenerationService.generateReportsForPage(anyString(), anyInt(), anyInt()))
                .thenReturn(Mono.empty());

        reportJobs.generateNextReportBatch();

        ArgumentCaptor<String> dateCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> pageCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> pageSizeCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(reportGenerationService, times(1))
                .generateReportsForPage(dateCaptor.capture(), pageCaptor.capture(), pageSizeCaptor.capture());

        assertThat(dateCaptor.getValue()).isEqualTo(GENERATION_DATE);
        assertThat(pageCaptor.getValue()).isEqualTo(0);
        assertThat(pageSizeCaptor.getValue()).isEqualTo(PAGE_SIZE);
        verify(backendTaskClient, never()).getCount(anyString());
    }
}

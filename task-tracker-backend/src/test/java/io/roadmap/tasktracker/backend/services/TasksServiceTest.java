package io.roadmap.tasktracker.backend.services;

import io.roadmap.tasktracker.backend.dto.TaskCreateRequest;
import io.roadmap.tasktracker.backend.dto.TaskResponse;
import io.roadmap.tasktracker.backend.dto.UserSummary;
import io.roadmap.tasktracker.backend.dto.UserWithTasksResponse;
import io.roadmap.tasktracker.backend.entities.Task;
import io.roadmap.tasktracker.backend.entities.User;
import io.roadmap.tasktracker.backend.exceptions.InternalServerErrorException;
import io.roadmap.tasktracker.backend.exceptions.TaskNotFoundException;
import io.roadmap.tasktracker.backend.mappers.TaskMapper;
import io.roadmap.tasktracker.backend.mappers.UserReportMapper;
import io.roadmap.tasktracker.backend.repositories.TasksRepository;
import io.roadmap.tasktracker.backend.repositories.UsersRepository;
import liquibase.exception.DatabaseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link TasksService}. No Spring context, no database.
 * <p>
 * Pinned behaviors (deliberate, read from source — do not "fix" silently):
 * <ul>
 *   <li>{@code create()} builds the entity manually, resolves the owner via
 *       {@code usersRepository.getReferenceById(UUID)} and FORCES {@code finished=false}.</li>
 *   <li>{@code findById()} does NOT filter by owner (repository loads by id only, @EntityGraph "user"),
 *       so a cross-user read is possible. These tests pin the CURRENT behavior.</li>
 *   <li>{@code delete()}/{@code update()} enforce ownership inside the repository WHERE clause
 *       ({@code t.user.id = :userId}); the service translates affected-rows == 0 into
 *       {@link TaskNotFoundException}.</li>
 *   <li>Error translation: any {@code DataAccessException} is wrapped into
 *       {@code liquibase.exception.DatabaseException}; anything else becomes
 *       {@link InternalServerErrorException} carrying the original message.</li>
 *   <li>Report pagination: 1-based pages, offset = (page - 1) * pageSize.</li>
 * </ul>
 * The injected {@code Scheduler} is replaced with {@link Schedulers#immediate()} so every
 * {@code Mono.fromCallable} + {@code subscribeOn} runs synchronously inside {@link StepVerifier}.
 */
@ExtendWith(MockitoExtension.class)
class TasksServiceTest {

    private static final UUID TASK_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String TASK_ID_STR = TASK_ID.toString();
    private static final String USER_ID_STR = USER_ID.toString();
    private static final String USER_LOGIN = "alice";
    private static final String DESCRIPTION = "Buy milk";
    private static final LocalDateTime FROM_DATE = LocalDateTime.of(2026, 11, 1, 0, 0);

    @Mock
    private TasksRepository tasksRepository;
    @Mock
    private UsersRepository usersRepository;
    // Required by the Lombok-generated constructor but unused by the current active code paths
    // (only referenced in commented-out code) — mocked, never stubbed (strict stubs friendly).
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private TaskMapper taskMapper;
    @Mock
    private UserReportMapper userReportMapper;

    @Captor
    private ArgumentCaptor<Task> taskCaptor;

    private TasksService tasksService;

    @BeforeEach
    void setUp() {
        // Constructor parameter order mirrors the @RequiredArgsConstructor field order:
        // tasksRepository, usersRepository, dbScheduler, transactionTemplate, taskMapper, userReportMapper.
        tasksService = new TasksService(
                tasksRepository,
                usersRepository,
                Schedulers.immediate(),
                transactionTemplate,
                taskMapper,
                userReportMapper);
    }

    // ------------------------------------------------------------------
    // create()
    // ------------------------------------------------------------------

    @Test
    @DisplayName("create: happy path saves entity with user reference and forced finished=false, maps to response")
    void create_happyPath_savesEntityWithUserReferenceAndForcedFalse() {
        TaskCreateRequest dto = new TaskCreateRequest(DESCRIPTION);
        User owner = user(USER_ID, USER_LOGIN);
        when(usersRepository.getReferenceById(USER_ID)).thenReturn(owner);
        when(tasksRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));
        // The real MapStruct mapper builds UserData from the userId/userLogin strings passed by the
        // service (auth context), and copies id/description/isFinished/createdAt from the entity.
        when(taskMapper.toResponse(any(Task.class), eq(USER_ID_STR), eq(USER_LOGIN), eq(false)))
                .thenAnswer(inv -> responseFrom(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2), inv.getArgument(3)));

        StepVerifier.create(tasksService.create(dto, USER_ID_STR, USER_LOGIN))
                .assertNext(response -> {
                    assertThat(response.description()).isEqualTo(DESCRIPTION);
                    assertThat(response.isFinished()).isFalse();
                    assertThat(response.user().id()).isEqualTo(USER_ID_STR);
                    assertThat(response.user().login()).isEqualTo(USER_LOGIN);
                })
                .verifyComplete();

        verify(usersRepository).getReferenceById(USER_ID);
        verify(tasksRepository).save(taskCaptor.capture());
        Task saved = taskCaptor.getValue();
        assertThat(saved.getUser()).isSameAs(owner); // user reference resolved via getReferenceById
        assertThat(saved.getDescription()).isEqualTo(DESCRIPTION);
        assertThat(saved.isFinished()).isFalse(); // create() FORCES finished=false regardless of input
    }

    @Test
    @DisplayName("create: DataIntegrityViolationException is wrapped into liquibase DatabaseException (pinned)")
    void create_saveFailsWithDataIntegrityViolation_wrapsInLiquibaseDatabaseException() {
        when(usersRepository.getReferenceById(USER_ID)).thenReturn(user(USER_ID, USER_LOGIN));
        when(tasksRepository.save(any(Task.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        // PIN: the error-translation switch wraps ANY DataAccessException into
        // liquibase.exception.DatabaseException, keeping the original exception as the cause.
        StepVerifier.create(tasksService.create(new TaskCreateRequest(DESCRIPTION), USER_ID_STR, USER_LOGIN))
                .expectErrorSatisfies(e -> assertThat(e)
                        .isInstanceOf(DatabaseException.class)
                        .hasCauseInstanceOf(DataIntegrityViolationException.class))
                .verify();
    }

    @Test
    @DisplayName("create: unexpected (non-DataAccess) save failure becomes InternalServerErrorException with original message")
    void create_saveFailsUnexpectedly_mapsToInternalServerErrorException() {
        when(usersRepository.getReferenceById(USER_ID)).thenReturn(user(USER_ID, USER_LOGIN));
        when(tasksRepository.save(any(Task.class))).thenThrow(new IllegalStateException("boom"));

        StepVerifier.create(tasksService.create(new TaskCreateRequest(DESCRIPTION), USER_ID_STR, USER_LOGIN))
                .expectErrorSatisfies(e -> assertThat(e)
                        .isInstanceOf(InternalServerErrorException.class)
                        .hasMessage("boom")) // default branch passes e.getMessage()
                .verify();
    }

    // ------------------------------------------------------------------
    // delete()
    // ------------------------------------------------------------------

    @Test
    @DisplayName("delete: 1 affected row completes empty and deletes with EXACT (taskId, userId) args")
    void delete_oneRowAffected_completesEmptyAndDeletesWithExactOwnerAndTaskId() {
        // Ownership comes from the auth context: the service must pass BOTH args through unmodified.
        when(tasksRepository.deleteTaskByIdAndUser(TASK_ID, USER_ID)).thenReturn(1);

        StepVerifier.create(tasksService.delete(TASK_ID_STR, USER_ID_STR))
                .verifyComplete();

        verify(tasksRepository).deleteTaskByIdAndUser(TASK_ID, USER_ID); // asserts BOTH args
    }

    @Test
    @DisplayName("delete: 0 affected rows throws TaskNotFoundException")
    void delete_zeroRowsAffected_throwsTaskNotFoundException() {
        when(tasksRepository.deleteTaskByIdAndUser(TASK_ID, USER_ID)).thenReturn(0);

        StepVerifier.create(tasksService.delete(TASK_ID_STR, USER_ID_STR))
                .expectErrorSatisfies(e -> assertThat(e)
                        .isInstanceOf(TaskNotFoundException.class)
                        .hasMessage("task not found")) // exact message from TaskNotFoundException
                .verify();
    }

    // ------------------------------------------------------------------
    // update()
    // ------------------------------------------------------------------

    @Test
    @DisplayName("update: 1 affected row completes empty and updates with EXACT (taskId, userId, finished) args")
    void update_oneRowAffected_completesEmptyAndUpdatesWithExactArgs() {
        when(tasksRepository.updateTaskByIdAndUser(TASK_ID, USER_ID, true)).thenReturn(1);

        StepVerifier.create(tasksService.update(TASK_ID_STR, USER_ID_STR, true))
                .verifyComplete();

        verify(tasksRepository).updateTaskByIdAndUser(TASK_ID, USER_ID, true); // asserts ALL THREE args
    }

    @Test
    @DisplayName("update: 0 affected rows throws TaskNotFoundException")
    void update_zeroRowsAffected_throwsTaskNotFoundException() {
        when(tasksRepository.updateTaskByIdAndUser(TASK_ID, USER_ID, true)).thenReturn(0);

        StepVerifier.create(tasksService.update(TASK_ID_STR, USER_ID_STR, true))
                .expectErrorSatisfies(e -> assertThat(e)
                        .isInstanceOf(TaskNotFoundException.class)
                        .hasMessage("task not found"))
                .verify();
    }

    // ------------------------------------------------------------------
    // findById()
    // ------------------------------------------------------------------

    @Test
    @DisplayName("findById: found task maps entity + embedded user (EntityGraph) into response")
    void findById_taskFound_mapsEntityAndEmbeddedUserIntoResponse() {
        // NOTE: findById does NOT filter by owner — repository loads by id only (@EntityGraph "user").
        // This test pins the current (cross-user-readable) behavior; ownership is NOT checked here.
        Task task = task(TASK_ID, user(USER_ID, USER_LOGIN), "Write report", true);
        when(tasksRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
        // The service maps the user data from the EMBEDDED entity user (task.getUser().getId()),
        // not from the task id — pinned by the exact-arg stub below (USER_ID_STR as 2nd arg).
        when(taskMapper.toResponse(task, USER_ID_STR, USER_LOGIN, true))
                .thenAnswer(inv -> responseFrom(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2), inv.getArgument(3)));

        StepVerifier.create(tasksService.findById(TASK_ID_STR))
                .assertNext(response -> {
                    assertThat(response.id()).isEqualTo(TASK_ID_STR);
                    assertThat(response.description()).isEqualTo("Write report");
                    assertThat(response.isFinished()).isTrue();
                    assertThat(response.user().id()).isEqualTo(USER_ID_STR);   // mapped from task.user.id
                    assertThat(response.user().login()).isEqualTo(USER_LOGIN); // mapped from task.user.login
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("findById: absent task is re-wrapped into InternalServerErrorException (pinned 500, unlike delete/update 404)")
    void findById_taskAbsent_isRewrappedIntoInternalServerError() {
        // PIN (actual behavior): the TaskNotFoundException raised by switchIfEmpty is caught by the
        // trailing onErrorResume and re-wrapped by its default branch into InternalServerErrorException
        // carrying the original "task not found" message -> HTTP 500. This diverges from delete/update,
        // where TaskNotFoundException survives the error translation (HTTP 404). Likely a defect
        // worth a follow-up ticket.
        when(tasksRepository.findById(TASK_ID)).thenReturn(Optional.empty());

        StepVerifier.create(tasksService.findById(TASK_ID_STR))
                .expectErrorSatisfies(e -> assertThat(e)
                        .isInstanceOf(InternalServerErrorException.class)
                        .hasMessage("task not found"))
                .verify();
    }

    @Test
    @DisplayName("findById: malformed UUID string maps to InternalServerErrorException (pinned 500, not 404)")
    void findById_malformedUuidString_mapsToInternalServerErrorException() {
        // PIN: UUID.fromString fails inside the fromCallable with IllegalArgumentException, which is
        // NOT a DataAccessException, so the default branch turns it into InternalServerErrorException.
        // A malformed id therefore yields a 500 (via the global handler), never a 404.
        StepVerifier.create(tasksService.findById("definitely-not-a-uuid"))
                .expectErrorSatisfies(e -> assertThat(e)
                        .isInstanceOf(InternalServerErrorException.class)
                        .hasMessageContaining("Invalid UUID string"))
                .verify();

        verifyNoInteractions(tasksRepository); // parsing fails before any repository access
    }

    // ------------------------------------------------------------------
    // findAllByUserId()
    // ------------------------------------------------------------------

    @Test
    @DisplayName("findAllByUserId: no tasks returns an empty (completing) flux")
    void findAllByUserId_noTasks_returnsEmptyFlux() {
        when(tasksRepository.findAllByUser_Id(USER_ID)).thenReturn(List.of());

        StepVerifier.create(tasksService.findAllByUserId(USER_ID))
                .expectNextCount(0)
                .verifyComplete();
    }

    @Test
    @DisplayName("findAllByUserId: three tasks mapped in repository order with per-task user data")
    void findAllByUserId_threeTasks_mapsAllResponsesInRepositoryOrder() {
        User alice = user(UUID.fromString("33333333-3333-3333-3333-333333333331"), "alice");
        User bob = user(UUID.fromString("33333333-3333-3333-3333-333333333332"), "bob");
        List<Task> tasks = List.of(
                task(UUID.randomUUID(), alice, "first", false),
                task(UUID.randomUUID(), bob, "second", true),
                task(UUID.randomUUID(), bob, "third", false));
        when(tasksRepository.findAllByUser_Id(USER_ID)).thenReturn(tasks);
        when(taskMapper.toResponse(any(Task.class), anyString(), anyString(), anyBoolean()))
                .thenAnswer(inv -> responseFrom(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2), inv.getArgument(3)));

        // Flux preserves the List order returned by the repository — pinned via distinct descriptions.
        StepVerifier.create(tasksService.findAllByUserId(USER_ID))
                .expectNextMatches(r -> "first".equals(r.description()) && "alice".equals(r.user().login()))
                .expectNextMatches(r -> "second".equals(r.description()) && "bob".equals(r.user().login()))
                .expectNextMatches(r -> "third".equals(r.description()) && "bob".equals(r.user().login()))
                .verifyComplete();
    }

    // ------------------------------------------------------------------
    // findUserReports()
    // ------------------------------------------------------------------

    @Test
    @DisplayName("findUserReports: 1-based page -> offset=(page-1)*pageSize; summaries mapped to reports")
    void findUserReports_computesOffsetFromOneBasedPage_andMapsSummaries() {
        // page=3, pageSize=10 -> offset=20, limit=10. Repository signature order is (fromDate, limit, offset).
        UserSummary summary = mock(UserSummary.class);
        UserWithTasksResponse mapped = new UserWithTasksResponse("alice", USER_ID_STR, "alice@example.com", List.of("task A"));
        when(usersRepository.findAllSummaries(FROM_DATE, 10, 20)).thenReturn(List.of(summary));
        when(userReportMapper.toReport(summary)).thenReturn(mapped);

        StepVerifier.create(tasksService.findUserReports(FROM_DATE, 10, 3))
                .expectNext(mapped)
                .verifyComplete();

        verify(usersRepository).findAllSummaries(FROM_DATE, 10, 20); // pins offset=(3-1)*10 and limit=10
        verify(userReportMapper).toReport(summary);
    }

    // ------------------------------------------------------------------
    // countUsersWithFinishedTasks()
    // ------------------------------------------------------------------

    @Test
    @DisplayName("countUsersWithFinishedTasks: returns the repository count value as-is")
    void countUsersWithFinishedTasks_returnsRepositoryCountAsIs() {
        when(usersRepository.countUsersWithFinishedTasks(FROM_DATE)).thenReturn(42L);

        StepVerifier.create(tasksService.countUsersWithFinishedTasks(FROM_DATE))
                .expectNext(42L)
                .verifyComplete();

        verify(usersRepository).countUsersWithFinishedTasks(FROM_DATE);
    }

    // ------------------------------------------------------------------
    // Test fixtures (real Lombok entities — no mocking needed)
    // ------------------------------------------------------------------

    private static User user(UUID id, String login) {
        User user = new User();
        user.setId(id);
        user.setLogin(login);
        return user;
    }

    private static Task task(UUID id, User user, String description, boolean finished) {
        Task task = new Task();
        task.setId(id);
        task.setUser(user);
        task.setDescription(description);
        task.setFinished(finished);
        return task;
    }

    /**
     * Mimics the real MapStruct {@code TaskMapper.toResponse} mapping:
     * {@code UserData} is built from the userId/userLogin arguments passed by the service,
     * while id/description/isFinished/createdAt are copied from the entity.
     */
    private static TaskResponse responseFrom(Task task, String userId, String userLogin, boolean finished) {
        return new TaskResponse(
                task.getId() == null ? null : task.getId().toString(),
                userId,
                userLogin,
                task.getDescription(),
                finished,
                task.getCreatedAt() == null ? null : task.getCreatedAt().toString());
    }
}

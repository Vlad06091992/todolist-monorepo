import { AsyncPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxChange, MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatToolbarModule } from '@angular/material/toolbar';
import { Router } from '@angular/router';

import { AuthService } from '../../core/auth.service';
import { extractErrorMessage } from '../../core/error-mapping';
import { TaskResponse } from '../../core/models';
import { TasksService } from '../../core/tasks.service';

import { AddTaskDialogComponent } from './add-task-dialog.component';
import { ConfirmDialogComponent, ConfirmDialogData } from './confirm-dialog.component';

/**
 * Главная страница: список задач, отметка «выполнено», удаление с подтверждением,
 * создание через диалог добавления, тулбар с пользователем и выходом.
 *
 * Состояние списка — только из TasksService через async pipe (приложение zoneless,
 * ручного CD нет). Действия командные: пессимистичные мутации сервиса, ошибки — снекбар.
 * Диалог создания сам вызывает TasksService.create и закрывается после успеха —
 * здесь достаточно открыть его.
 */
@Component({
  selector: 'app-tasks',
  imports: [
    AsyncPipe,
    MatToolbarModule,
    MatListModule,
    MatCheckboxModule,
    MatButtonModule,
    MatIconModule,
    MatDialogModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
  ],
  templateUrl: './tasks.component.html',
  styleUrl: './tasks.component.scss',
})
export class TasksComponent implements OnInit {
  protected readonly tasksService = inject(TasksService);
  protected readonly authService = inject(AuthService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly router = inject(Router);

  ngOnInit(): void {
    // Ошибку (в т.ч. 401) сознательно глотаем: интерцептор сам очистит сессию
    // и сделает редирект на /login — здесь реагировать нечем и не нужно.
    this.tasksService.load().subscribe({ error: () => {} });
    // Тот же приём: наполняет тулбар логином и служит живым 401-путём.
    this.authService.loadUser().subscribe({ error: () => {} });
  }

  /** Выход: очистить сессию и вернуться на страницу входа. */
  logout(): void {
    this.authService.logout();
    void this.router.navigate(['/login']);
  }

  /**
   * Переключение «выполнено» — пессимистично: чекбокс не щёлкаем локально,
   * состояние вернётся из перезагруженного списка после PUT → reload.
   */
  setFinished(task: TaskResponse, event: MatCheckboxChange): void {
    this.tasksService.setFinished(task.id, event.checked).subscribe({
      error: (err: HttpErrorResponse) => this.showError(err),
    });
  }

  /** Удаление задачи — только после подтверждения в диалоге. */
  deleteTask(task: TaskResponse): void {
    this.dialog
      .open<ConfirmDialogComponent, ConfirmDialogData, boolean>(ConfirmDialogComponent, {
        data: { message: 'Удалить задачу?' },
      })
      .afterClosed()
      .subscribe((confirmed) => {
        if (confirmed === true) {
          this.tasksService.remove(task.id).subscribe({
            error: (err: HttpErrorResponse) => this.showError(err),
          });
        }
      });
  }

  /** Открытие диалога создания задачи; список диалог обновит сам (create → reload). */
  openAddDialog(): void {
    this.dialog.open<AddTaskDialogComponent, void, void>(AddTaskDialogComponent, {
      width: '480px',
    });
  }

  private showError(err: HttpErrorResponse): void {
    this.snackBar.open(extractErrorMessage(err), undefined, { duration: 4000 });
  }
}

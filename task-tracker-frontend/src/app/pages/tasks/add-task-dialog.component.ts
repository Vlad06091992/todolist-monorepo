import { AsyncPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject } from '@angular/core';
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { BehaviorSubject, finalize } from 'rxjs';

import { extractErrorMessage, extractFieldErrors } from '../../core/error-mapping';
import { TasksService } from '../../core/tasks.service';

/**
 * Валидатор «не пусто после trim»: Validators.required пропускает строки из одних
 * пробельных символов, эта проверка их отсекает.
 */
function noWhitespace(control: AbstractControl): ValidationErrors | null {
  debugger
  const value = typeof control.value === 'string' ? control.value : '';
  // debugger
  let newVar = value.trim().length === 0 ? { whitespace: true } : null;
  // debugger
  return newVar;
  // return {
  //   whitespace: false
  // };
}

/**
 * Диалог создания задачи: одно поле «Описание задачи» и кнопки «Отмена»/«Создать».
 * Сам вызывает TasksService.create (пессимично) и закрывается только при успехе;
 * ошибка валидации бэкенда формы {description: msg} показывается инлайн под полем,
 * остальные ошибки — снекбаром, диалог остаётся открытым.
 * Состояние отправки — BehaviorSubject + async pipe (приложение zoneless).
 */
@Component({
  selector: 'app-add-task-dialog',
  imports: [
    AsyncPipe,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
  ],
  template: `
    <h2 mat-dialog-title>Новая задача</h2>

    <mat-dialog-content>
      <div [formGroup]="form">
      <mat-form-field appearance="outline" class="add-task-dialog__field">
        <mat-label>Описание задачи</mat-label>
        <pre style="background: #eee; padding: 8px; font-size: 11px;">
          touched: {{ form.controls.description.touched }}
          invalid: {{ form.controls.description.invalid }}
          errors:  {{ JSON.stringify(form.controls.description.errors)  }}
        </pre>
        <textarea matInput rows="3" formControlName="description"></textarea>
        @if (form.controls.description.invalid && form.controls.description.touched) {
          @if (form.controls.description.errors?.['server']; as serverMessage) {
            <mat-error>{{ serverMessage }}</mat-error>
          } @else {
            <mat-error>Опишите задачу</mat-error>
          }
        }
      </mat-form-field>
      </div>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close>Отмена</button>
      <button
        mat-raised-button
        color="primary"
        type="button"
        (click)="create()"
        [disabled]="form.invalid || (submitting$ | async)"
      >
        Создать
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .add-task-dialog__field {
      width: 100%;
    }
  `,
})
export class AddTaskDialogComponent {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly dialogRef = inject<MatDialogRef<AddTaskDialogComponent>>(MatDialogRef);
  private readonly tasksService = inject(TasksService);
  private readonly snackBar = inject(MatSnackBar);

  /** Идёт запрос на создание — блокирует кнопку, рендерится через async pipe. */
  protected readonly submitting$ = new BehaviorSubject<boolean>(false);

  protected readonly form = this.fb.group({
    description: ['', [Validators.required, noWhitespace]],
  });

  /** Создание задачи: POST /tasks → снекбар «Задача создана» + закрытие диалога. */
  protected create(): void {
    debugger
    if (this.form.invalid) {
      debugger
      this.form.markAllAsTouched();
      return;
    }

    this.submitting$.next(true);
    const description = this.form.getRawValue().description.trim();

    this.tasksService
      .create(description)
      .pipe(finalize(() => this.submitting$.next(false)))
      .subscribe({
        next: () => {
          this.snackBar.open('Задача создана', undefined, { duration: 4000 });
          this.dialogRef.close();
        },
        error: (err: HttpErrorResponse) => this.handleError(err),
      });
  }

  protected onChange(args:any): void {
    debugger
    console.log(args);
  }

  ngOnInit() {
    this.form.controls.description.valueChanges.subscribe(val => {
      console.log('Текущее значение:', val);
    });
  }

  /**
   * Ошибки create: форма {description: msg} — инлайн под полем (setErrors({server})),
   * текст ошибки — дополнительно снекбаром; сброс submitting уже сделан в finalize.
   */
  private handleError(err: HttpErrorResponse): void {
    debugger
    const descriptionMessage = extractFieldErrors(err)?.['description'];
    if (descriptionMessage !== undefined) {
      this.form.controls.description.setErrors({ server: descriptionMessage });
    }
    this.snackBar.open(extractErrorMessage(err), undefined, { duration: 4000 });
  }

  protected readonly JSON = JSON;
}

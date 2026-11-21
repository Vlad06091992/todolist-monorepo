import { AsyncPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router, RouterLink } from '@angular/router';
import { BehaviorSubject, EMPTY, catchError, finalize, switchMap } from 'rxjs';

import { AuthService } from '../../core/auth.service';
import { extractErrorMessage, extractFieldErrors } from '../../core/error-mapping';

/**
 * Страница регистрации: логин 4–30, email, пароль 3–30 — зеркалим ограничения
 * бэкенда (dto/UserCreateRequest.java: @Size(min=4,max=30) / @Email / @Size(min=3,max=30)).
 *
 * Успех signUp → автоматический вход с теми же кредами (switchMap) → /tasks.
 * Ошибки signUp: форма {field: msg} → инлайн в соответствующие поля;
 * бизнес-форма {message} (напр. дубликат) → снекбар + ошибка на поле login.
 *
 * Приложение zoneless — состояние отправки только через async pipe.
 */
@Component({
  selector: 'app-register',
  imports: [
    AsyncPipe,
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    RouterLink,
  ],
  templateUrl: './register.component.html',
  styleUrl: './register.component.scss',
})
export class RegisterComponent {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);

  /** Идёт отправка запроса — рендерится в шаблоне через async pipe. */
  protected readonly submitting$ = new BehaviorSubject<boolean>(false);

  protected readonly form = this.fb.group({
    login: ['', [Validators.required, Validators.minLength(4), Validators.maxLength(30)]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(30)]],
  });

  submit(): void {
    // Серверная ошибка делает форму invalid и должна быть сброшена,
    // чтобы пользователь мог повторить отправку без правки полей.
    this.clearServerErrors();

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting$.next(true);
    const { login, email, password } = this.form.getRawValue();

    // Одна подписка: signUp → switchMap(signIn) → навигация. Ошибки signUp
    // перехватываются ДО switchMap (инлайн/снекбар), ошибки signIn — наружу.
    this.authService
      .signUp({ login, email, password })
      .pipe(
        catchError((err: HttpErrorResponse) => {
          this.handleSignUpError(err);
          return EMPTY;
        }),
        switchMap(() => this.authService.signIn({ login, password })),
        finalize(() => this.submitting$.next(false)),
      )
      .subscribe({
        next: () => {
          void this.router.navigateByUrl('/tasks');
        },
        error: (err: HttpErrorResponse) => {
          // Регистрация прошла, авто-вход не удался — пусть войдёт вручную.
          this.snackBar.open(extractErrorMessage(err), undefined, { duration: 4000 });
          void this.router.navigateByUrl('/login');
        },
      });
  }

  /** Ошибки signUp: {field: msg} → инлайн по полям; {message} → снекбар + поле login. */
  private handleSignUpError(err: HttpErrorResponse): void {
    const fieldErrors = extractFieldErrors(err);
    if (fieldErrors !== null) {
      for (const key of ['login', 'email', 'password'] as const) {
        const message = fieldErrors[key];
        if (message !== undefined) {
          this.form.controls[key].setErrors({ server: message });
        }
      }
      return;
    }

    const message = extractErrorMessage(err);
    this.snackBar.open(message, undefined, { duration: 4000 });
    this.form.controls.login.setErrors({ server: message });
  }

  private clearServerErrors(): void {
    for (const control of [
      this.form.controls.login,
      this.form.controls.email,
      this.form.controls.password,
    ]) {
      control.setErrors(null);
    }
  }
}

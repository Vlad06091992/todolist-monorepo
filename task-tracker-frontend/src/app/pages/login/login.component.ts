import { AsyncPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { BehaviorSubject, finalize } from 'rxjs';

import { AuthService } from '../../core/auth.service';
import { extractErrorMessage } from '../../core/error-mapping';

/**
 * Страница входа: реактивная форма логин/пароль, состояние отправки — через
 * BehaviorSubject + async pipe (приложение zoneless, ручной CD нет).
 */
@Component({
  selector: 'app-login',
  imports: [
    AsyncPipe,
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    RouterLink,
  ],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly snackBar = inject(MatSnackBar);

  /** Идёт отправка запроса — рендерится в шаблоне через async pipe. */
  protected readonly submitting$ = new BehaviorSubject<boolean>(false);

  protected readonly form = this.fb.group({
    login: ['', Validators.required],
    password: ['', Validators.required],
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting$.next(true);
    const { login, password } = this.form.getRawValue();

    this.authService
      .signIn({ login, password })
      .pipe(finalize(() => this.submitting$.next(false)))
      .subscribe({
        next: () => {
          void this.router.navigateByUrl(this.resolveReturnUrl());
        },
        error: (err: HttpErrorResponse) => {
          this.snackBar.open(extractErrorMessage(err, 'Неверный логин или пароль'), undefined, {
            duration: 4000,
          });
        },
      });
  }

  /**
   * Куда идти после входа: returnUrl из query — ТОЛЬКО если это внутренний путь
   * (начинается с '/'), иначе '/tasks'. Защита от открытого редиректа.
   */
  private resolveReturnUrl(): string {
    const raw = this.route.snapshot.queryParamMap.get('returnUrl');
    return raw !== null && raw.startsWith('/') ? raw : '/tasks';
  }
}

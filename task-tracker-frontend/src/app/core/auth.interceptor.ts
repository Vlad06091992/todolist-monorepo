import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { AuthService } from './auth.service';

/** Публичные эндпоинты: токен не прикрепляется, их собственный 401 не триггерит редирект. */
function isAuthEndpoint(url: string): boolean {
  return url.includes('/auth/sign-in') || url.includes('/auth/sign-up');
}

/** Дедупликация параллельных 401: один редирект на /login за раз. */
let isRedirecting = false;

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const token = authService.getToken();
  const request =
    token && !isAuthEndpoint(req.url)
      ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
      : req;

  return next(request).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status === 401) {
        // 401 от sign-in/sign-up — обычная ошибка входа, страница логина сама покажет сообщение.
        if (isAuthEndpoint(req.url)) {
          return throwError(() => err);
        }
        // Истёк/невалиден JWT: очистить сессию и увести на /login (кроме случая,
        // когда мы уже там — иначе цикл редиректов).
        if (!isRedirecting && router.url !== '/login') {
          isRedirecting = true;
          authService.logout();
          void router
            .navigate(['/login'])
            .finally(() => {
              isRedirecting = false;
            });
        }
      }
      return throwError(() => err);
    }),
  );
};

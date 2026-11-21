import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from './auth.service';

/** Для авторизованных: пускаем. Для гостей: на /login с возвратом на исходный URL. */
export const authGuard: CanActivateFn = (_route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  if (authService.getToken() !== null) {
    return true;
  }
  return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

/** Для гостей: пускаем. Для авторизованных: сразу на /tasks. */
export const guestGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);
  return authService.getToken() !== null ? router.createUrlTree(['/tasks']) : true;
};

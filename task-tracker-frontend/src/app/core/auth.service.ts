import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { BehaviorSubject, Observable, map, switchMap, tap } from 'rxjs';

import { AuthRequest, AuthResponse, CurrentUser, UserCreateRequest } from './models';

/** Ключ хранения JWT. Доступ к localStorage — ТОЛЬКО в этом файле. */
const TOKEN_KEY = 'task_tracker_jwt';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  /** Текущий пользователь для шаблонов через async pipe (zoneless). */
  readonly currentUser$ = new BehaviorSubject<CurrentUser | null>(null);

  /**
   * Вход: POST /auth/sign-in → сохранить токен → загрузить /user/me → опубликовать в currentUser$.
   * Цепочка tap → switchMap → tap → map, без вложенных subscribe.
   */
  signIn(req: AuthRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>('/api/auth/sign-in', req).pipe(
      tap((res) => this.persistToken(res.token)),
      switchMap((res) => this.me().pipe(map((user) => ({ res, user })))),
      tap(({ user }) => this.currentUser$.next(user)),
      map(({ res }) => res),
    );
  }

  /**
   * Регистрация: POST /auth/sign-up. Бэкенд отвечает 200 text/plain
   * 'User signed up successfully' (ResponseEntity<String>) — дефолтный JSON-responseType
   * кинул бы ошибку парсинга на УСПЕШНОМ ответе, поэтому responseType: 'text'.
   */
  signUp(req: UserCreateRequest): Observable<string> {
    return this.http.post('/api/auth/sign-up', req, { responseType: 'text' });
  }

  /** GET /user/me — данные текущего пользователя из JWT. */
  me(): Observable<CurrentUser> {
    return this.http.get<CurrentUser>('/api/user/me');
  }

  /**
   * Загрузить текущего пользователя и опубликовать в currentUser$.
   * Вызывается страницами при инициализации; ошибки ПРОЗРАЧНО пробрасываются дальше,
   * чтобы интерцептор среагировал на 401 (очистка + редирект на /login).
   */
  loadUser(): Observable<CurrentUser> {
    return this.me().pipe(tap((user) => this.currentUser$.next(user)));
  }

  /** Токен для интерцептора (сам интерцептор localStorage не трогает). */
  getToken(): string | null {
    return this.readToken();
  }

  /** Выход: удалить токен и сбросить текущего пользователя. */
  logout(): void {
    this.clearToken();
    this.currentUser$.next(null);
  }

  // --- приватные помощники хранения; try/catch — на случай запрета localStorage (private browsing)

  private persistToken(token: string): void {
    try {
      localStorage.setItem(TOKEN_KEY, token);
    } catch {
      // localStorage недоступен — токен не сохранится, сессия только на время страницы
    }
  }

  private readToken(): string | null {
    try {
      return localStorage.getItem(TOKEN_KEY);
    } catch {
      return null;
    }
  }

  private clearToken(): void {
    try {
      localStorage.removeItem(TOKEN_KEY);
    } catch {
      // localStorage недоступен — нечего удалять
    }
  }
}

import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { BehaviorSubject, Observable, finalize, map, mergeMap } from 'rxjs';

import { TaskResponse } from './models';

/**
 * RxJS-стор задач (требование проекта — RxJS-first, без NgRx/сигнал-стора).
 * Компоненты читают tasks$/loading$ через async pipe (zoneless),
 * ошибки обрабатывают в своих .subscribe({ error }) — сервис снекбаров не показывает.
 * Мутации пессимистичные: сначала ответ сервера, затем перезагрузка списка.
 */
@Injectable({ providedIn: 'root' })
export class TasksService {
  private readonly http = inject(HttpClient);

  private readonly _tasks$ = new BehaviorSubject<TaskResponse[]>([]);
  readonly tasks$ = this._tasks$.asObservable();

  private readonly _loading$ = new BehaviorSubject<boolean>(false);
  readonly loading$ = this._loading$.asObservable();

  /** GET /tasks — список задач текущего пользователя → в стор. */
  load(): Observable<TaskResponse[]> {
    this._loading$.next(true);
    return this.http.get<TaskResponse[]>('/api/tasks').pipe(
      map((tasks) => {
        this._tasks$.next(tasks);
        return tasks;
      }),
      finalize(() => this._loading$.next(false)),
    );
  }

  /** POST /tasks {description} → перезагрузить список. */
  create(description: string): Observable<void> {
    this._loading$.next(true);
    return this.http.post<TaskResponse>('/api/tasks', { description }).pipe(
      mergeMap(() => this.load()),
      map(() => undefined),
      finalize(() => this._loading$.next(false)),
    );
  }

  /** PUT /tasks/{id} {finished} → перезагрузить список. */
  setFinished(id: string, finished: boolean): Observable<void> {
    this._loading$.next(true);
    return this.http.put<void>(`/api/tasks/${id}`, { finished }).pipe(
      mergeMap(() => this.load()),
      map(() => undefined),
      finalize(() => this._loading$.next(false)),
    );
  }

  /** DELETE /tasks/{id} → перезагрузить список. */
  remove(id: string): Observable<void> {
    this._loading$.next(true);
    return this.http.delete<void>(`/api/tasks/${id}`).pipe(
      mergeMap(() => this.load()),
      map(() => undefined),
      finalize(() => this._loading$.next(false)),
    );
  }
}

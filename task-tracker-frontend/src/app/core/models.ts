/** DTO-контракты task-tracker-backend. Источник: task-tracker-backend/docs/api.md */

export interface TaskUser {
  id: string;
  login: string;
}

export interface TaskResponse {
  id: string;
  user: TaskUser;
  description: string;
  isFinished: boolean;
  /** LocalDateTime без таймзоны, строка вида "2026-11-20T12:34:56".
   *  НЕ парсить через new Date() — строка без Z парсится как локальное время (сдвиг отображения). */
  createdAt: string;
}

export interface AuthRequest {
  login: string;
  password: string;
}

export interface AuthResponse {
  token: string;
}

export interface UserCreateRequest {
  login: string;
  email: string;
  password: string;
}

export interface CurrentUser {
  login: string;
  id: string;
}

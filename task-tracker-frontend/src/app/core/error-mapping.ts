import { HttpErrorResponse } from '@angular/common/http';

/**
 * Маппинг ошибок бэкенда (task-tracker-backend/docs/api.md:107-117 + недокументированные формы
 * из .omo/qa/stub-backend.mjs) в понятные пользователю сообщения.
 *
 * Формы тел ошибок (приоритет проверки):
 *  1. `{ "error": "..." }` — пропущен параметр запроса / ошибка валидации параметров (api.md:110)
 *  2. `{ "message": "..." }` — бизнес-ошибки 400/404/500 (api.md:111)
 *  3. строка (text/plain) — ResponseEntity<String>, напр. 'User signed up successfully'
 *  4. `{ "<field>": "текст" }` — валидация тела запроса (api.md:109)
 *  5. пустое тело со статусом 401 — дефолтный challenge JWT-фильтра WebFlux
 */

const BAD_CREDENTIALS_MESSAGE = 'Неверный логин или пароль';

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

/** Убирает окружающие двойные кавычки, если бэкенд вернул строку в кавычках. */
function stripSurroundingQuotes(text: string): string {
  return text.length >= 2 && text.startsWith('"') && text.endsWith('"') ? text.slice(1, -1) : text;
}

/** Первое строковое значение плоского объекта `{field: msg}`. */
function firstStringValue(object: Record<string, unknown>): string | null {
  for (const value of Object.values(object)) {
    if (typeof value === 'string' && value.length > 0) {
      return value;
    }
  }
  return null;
}

export function extractErrorMessage(
  err: HttpErrorResponse,
  fallback = 'Произошла ошибка. Попробуйте ещё раз.',
): string {
  const body: unknown = err.error;

  // Форма 1: {error: '...'}
  if (isPlainObject(body) && typeof body['error'] === 'string' && body['error'].length > 0) {
    // Недокументированное поведение реального бэкенда: BadCredentialsException на /auth/sign-in
    // НЕ обработан в GlobalExceptionHandler (GlobalExceptionHandler.java:19-79), поэтому WebFlux
    // отдаёт 500 с дефолтным телом {timestamp, path, status, error: 'Internal Server Error', ...}.
    // В этом бэкенде такая 500-я возникает ТОЛЬКО из-за неверных логина/пароля (подтверждено
    // по исходникам и воспроизведено в .omo/qa/stub-backend.mjs) → показываем дружелюбное сообщение.
    if (err.status === 500 && body['error'] === 'Internal Server Error') {
      return BAD_CREDENTIALS_MESSAGE;
    }
    return body['error'];
  }

  // Форма 2: {message: '...'} (бизнес-ошибки, напр. 'User already exists')
  if (isPlainObject(body) && typeof body['message'] === 'string' && body['message'].length > 0) {
    return body['message'];
  }

  // Форма 3: text/plain строка
  if (typeof body === 'string' && body.length > 0) {
    return stripSurroundingQuotes(body);
  }

  // Форма 4: {<field>: 'текст'} — валидация тела запроса
  if (isPlainObject(body)) {
    const fieldMessage = firstStringValue(body);
    if (fieldMessage !== null) {
      return fieldMessage;
    }
  }

  // Форма 5: 401 с пустым телом (нет Content-Type / пустой body) — истёк или невалиден JWT
  if (err.status === 401) {
    return BAD_CREDENTIALS_MESSAGE;
  }

  return fallback;
}

/**
 * Ошибки валидации полей в форме `{<field>: 'текст'}` (api.md:109).
 * Возвращает null, если тело НЕ этой формы (в т.ч. для {error}/{message}-форм).
 */
export function extractFieldErrors(err: HttpErrorResponse): Record<string, string> | null {
  const body: unknown = err.error;
  if (!isPlainObject(body) || 'error' in body || 'message' in body) {
    return null;
  }
  const fieldErrors: Record<string, string> = {};
  for (const [key, value] of Object.entries(body)) {
    if (typeof value === 'string') {
      fieldErrors[key] = value;
    }
  }
  return Object.keys(fieldErrors).length > 0 ? fieldErrors : null;
}

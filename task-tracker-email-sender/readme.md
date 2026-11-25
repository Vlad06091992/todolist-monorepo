# task-tracker-email-sender

Spring Boot сервис рассылки писем: **Spring Mail + Spring Kafka**.

## Как работает

1. `KafkaMessageListener` слушает топик **`notifications.email.requested`**.
2. Для каждого входящего события:
   - дедупликация по `eventId` через inbox-таблицу (`EventInboxService`, сущность `EventInbox`);
   - постановка в собственный outbox (`EventOutbox`) в одной транзакции с inbox-записью.
3. Релей `OutboxRelayScheduler` → `OutboxRelayService` каждые 5 секунд забирает события в статусе `WAITING`, отправляет письмо и переводит событие в `SENT`. После 3 неудачных попыток — `FAILED`, причина сохраняется в `last_error`.

## Письма

- приветствие при регистрации пользователя — `UserRegisteredEvent`;
- дневной отчёт по задачам — `EmailReportRequestedEvent`.

## Конфигурация почты

`MAIL_USERNAME` / `MAIL_PASSWORD` берутся из `.env` и подставляются в `spring.mail.*` (`application.yml`).

# task-tracker-summarization-server

NestJS-сервис суммаризации: интеграция с **GigaChat** (LLM) и **Kafka** (kafkajs).

## Как работает

1. `SummarizationConsumerService` (`summarization.consumer.ts`) слушает топик **`reports.summarization.requested`**.
   Вход — массив сводок пользователей (`UserSummary`: id, login, email, tasks[]).
2. `SummarizationService` (`summarization.service.ts`) строит промпт, вызывает **GigaChat** и парсит ответ.
3. Результат (массив `{login, message}`) публикуется в **`reports.summarization.completed`**.
   Kafka-заголовки (включая `CORRELATION_ID`) пробрасываются прозрачно — реализация RPC-паттерна со scheduler.

## Контракты

Типы (вход/выход/фигуры промпта) — в `src/contracts.ts`. Фикстуры для ручных проверок LLM — в `test/fixtures/`.

## Конфигурация (`.env`)

- `GIGACHAT_CREDENTIALS` — ключ доступа к GigaChat API;
- `APP_KAFKA_TOPIC_REPORTS_SUMMARIES_REQUESTED` / `APP_KAFKA_TOPIC_REPORTS_SUMMARIES_COMPLETED` — имена топиков;
- `KAFKA_BROKERS`, `KAFKA_CLIENT_ID`, `KAFKA_CONSUMER_GROUP_ID` — подключение к Kafka (есть дефолты).

## Скрипты

- `npm run build`, `npm run start:dev` — сборка и запуск в watch-режиме;
- `npm run lint` — ESLint.

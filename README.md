# todolist-monorepo — запуск из консоли

Task-tracker: Java-сервисы (Spring Boot 4, Java 21) + NestJS-сервис, связанные через Kafka.

Поток: **backend** (REST API, задачи) → **scheduler** (по расписанию собирает данные) → **summarization-server** (отчёт через GigaChat) → **email-sender** (письма).

## Требования

| Что | Версия | Зачем |
|---|---|---|
| JDK | 21 | все Gradle-сервисы (toolchain) |
| Node.js | 20+ | summarization-server |
| PostgreSQL | 16 | две БД (backend, email-sender) |
| Docker | — | Kafka (+ kafka-ui) |

## 1. Инфраструктура

Kafka — из корня репо (`docker-compose.yaml`: kafka :9092 + kafka-ui :8091):

```bash
docker compose up -d
```

PostgreSQL — в compose его **нет**, обе БД ждут на `localhost:5440`:

```bash
docker run -d --name task-tracker-postgres \
  -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres \
  -p 5440:5432 postgres:16

# создать обе БД (схему накатит Liquibase при старте сервисов)
docker exec task-tracker-postgres psql -U postgres \
  -c "CREATE DATABASE todolist" -c "CREATE DATABASE \"email-sender-db\""
```

## 2. Сервисы (4 терминала)

### backend — REST API, порт **8080**

```bash
cd task-tracker-backend
./gradlew bootRun
```

### email-sender — письма, порт **8077**

`.env` подхватывается сам (`spring.config.import: optional:file:.env[.properties]`),
запускать **из каталога сервиса**:

```bash
cd task-tracker-email-sender
./gradlew bootRun
```

### scheduler — планировщик, порт **8075**

```bash
cd task-tracker-scheduler
./gradlew bootRun
```

### summarization-server — GigaChat-отчёты, порт **3000**

`.env` читает сам (NestJS ConfigModule), запускать из каталога сервиса:

```bash
cd task-tracker-summarization-server
npm ci
npm run start:dev
```

## 3. Проверка

Kafka-ui: http://localhost:8091 (кластер `local`).

Сценарий через backend (base-path `/api`):

```bash
# регистрация (письмо-приветствие уйдёт через Kafka → email-sender)
curl -X POST http://localhost:8080/api/auth/sign-up \
  -H 'Content-Type: application/json' \
  -d '{"login":"testuser","email":"test@example.com","password":"secret1"}'

# вход — из ответа взять token
curl -X POST http://localhost:8080/api/auth/sign-in \
  -H 'Content-Type: application/json' \
  -d '{"login":"testuser","password":"secret1"}'

# текущий пользователь
curl http://localhost:8080/api/users/me -H 'Authorization: Bearer <TOKEN>'

# данные для отчёта + счётчик (их читает scheduler)
curl 'http://localhost:8080/api/reports/users-with-finished-tasks?from=2026-01-01T00:00:00&page=1&pageSize=20' \
  -H 'Authorization: Bearer <TOKEN>'
curl 'http://localhost:8080/api/reports/users-with-finished-tasks/count?from=2026-01-01T00:00:00'
```

Ручной прогон джоб scheduler'а (cron'ы: prep — 12:55, main — 13:00–13:59 каждые 3 мин):

```bash
curl -X POST http://localhost:8075/test/jobs/prep
curl -X POST http://localhost:8075/test/jobs/main
```

Ручной прогон summarization (в обход Kafka, напрямую в GigaChat):

```bash
curl -X POST http://localhost:3000/dev/send \
  -H 'Content-Type: application/json' \
  -d '{"payload":[{"login":"testuser","completedLast24h":["сделал задачу"],"activeTasks":1}]}'
```

## Порты и endpoints

| Сервис | Порт | БД | Ключевое |
|---|---|---|---|
| backend | 8080 | `todolist` @ 5440 | `/api/auth/*`, `/api/tasks*`, `/api/reports/*`, `/api/users/me` |
| email-sender | 8077 | `email-sender-db` @ 5440 | консьюмер `notifications.email.requested` |
| scheduler | 8075 | — | cron-джобы, RPC к summarization |
| summarization | 3000 | — | консьюмер `reports.summarization.requested` |
| kafka-ui | 8091 | — | http://localhost:8091 |
| postgres | 5440 | — | postgres/postgres |

## Примечания

- Схема БД создаётся **Liquibase** при старте backend/email-sender (`ddl-auto: validate`).
- Порядок старта Java-сервисов не критичен — Kafka-консьюмеры переподключаются сами; но чтобы видеть весь поток, запускай в порядке выше.
- Топики Kafka создаются автоматически (`AUTO_CREATE_TOPICS_ENABLE: true` в compose).
- Секреты: почта/GigaChat-ключ — в `.env` соответствующих сервисов (в репо лежат dev-значения), JWT-секрет backend'а — в `application.yaml`.
- Сборка без запуска: `./gradlew build` (Java) / `npm run build` (Nest).

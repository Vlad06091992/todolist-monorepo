# Запуск тестов в монорепо

В монорепо 4 сервиса. Быстрые юнит-тесты есть во всех четырёх; полный актуальный объём:
**backend 51 + email-sender 22 + scheduler 13 + summarization-server 16 = 102 теста.**

---

## ⚡ Одна команда — все тесты всех проектов

```bash
export PATH="$HOME/.nvm/versions/node/v24.16.0/bin:$PATH"
cd /learn_work/todolist-monorepo && \
(cd task-tracker-backend && ./gradlew test \
  --tests "io.roadmap.tasktracker.backend.security.*" \
  --tests "io.roadmap.tasktracker.backend.services.*" \
  --tests "io.roadmap.tasktracker.backend.kafka.*" -q) && \
(cd task-tracker-email-sender && ./gradlew test \
  --tests "io.roadmap.tasktracker.emailsender.services.*" \
  --tests "io.roadmap.tasktracker.emailsender.kafka.*" -q) && \
(cd task-tracker-scheduler && ./gradlew test \
  --tests "io.roadmap.tasktracker.scheduler.kafka.*" \
  --tests "io.roadmap.tasktracker.scheduler.report.*" \
  --tests "io.roadmap.tasktracker.scheduler.job.*" -q) && \
(cd task-tracker-summarization-server && npm test --silent) && \
echo "=== ALL TESTS PASSED ==="
```
for windows
```typescript
$env:PATH = "$HOME\.nvm\versions\node\v24.16.0\bin;$env:PATH"
Set-Location C:\learn_work\todolist-monorepo

Set-Location task-tracker-backend
./gradlew test `
  --tests "io.roadmap.tasktracker.backend.security.*" `
  --tests "io.roadmap.tasktracker.backend.services.*" `
  --tests "io.roadmap.tasktracker.backend.kafka.*" -q
if ($LASTEXITCODE -ne 0) { throw "backend tests failed" }
Set-Location ..

Set-Location task-tracker-email-sender
./gradlew test `
  --tests "io.roadmap.tasktracker.emailsender.services.*" `
  --tests "io.roadmap.tasktracker.emailsender.kafka.*" -q
if ($LASTEXITCODE -ne 0) { throw "email-sender tests failed" }
Set-Location ..

Set-Location task-tracker-scheduler
./gradlew test `
  --tests "io.roadmap.tasktracker.scheduler.kafka.*" `
  --tests "io.roadmap.tasktracker.scheduler.report.*" `
  --tests "io.roadmap.tasktracker.scheduler.job.*" -q
if ($LASTEXITCODE -ne 0) { throw "scheduler tests failed" }
Set-Location ..

Set-Location task-tracker-summarization-server
npm test --silent
if ($LASTEXITCODE -ne 0) { throw "summarization-server tests failed" }
Set-Location ..

Write-Host "=== ALL TESTS PASSED ==="
```

Финальный признак успеха — строка `=== ALL TESTS PASSED ===`.

Важно: фильтры `--tests "....<subpackage>.*"` сознательно исключают корневые
`*ApplicationTests` — им нужны живые Postgres (`localhost:5440`) и Kafka (`localhost:9092`),
а Postgres отсутствует даже в docker-compose. Полный `./gradlew test` без фильтров в
трёх Java-модулях упадёт на этих заглушках.

Красный лог `[AppController] Report generation failed ... gigachat down` в выводе
summarization-server — это **ожидаемый** лог из теста error-path, не падение.

---

## Предпосылки (на этой машине уже настроено)

| Что | Где | Кому нужно |
|---|---|---|
| JDK 21 (Temurin) | `~/.jdks/jdk-21.0.13.1+2`, прописан в `~/.gradle/gradle.properties` (`org.gradle.java.installations.paths`) | 3 Java-модуля |
| Прокси для Gradle | `~/.gradle/gradle.properties` (`systemProp.http(s).proxy*`) — пароль от прокси НЕ в репозитории | первый скач качает Gradle-дистрибутив и зависимости |
| Node ≥ 24.9 | `~/.nvm/versions/node/v24.16.0/bin` (в PATH: `export PATH=...`) | summarization-server (jest 30 требует `require(esm)`) |
| `npm install` | выполнен один раз в `task-tracker-summarization-server` | summarization-server |
| Docker | не нужен ни одному из юнит-наборов | — |

---

## task-tracker-backend (Spring Boot 4.1, WebFlux)

```bash
cd task-tracker-backend && ./gradlew test \
  --tests "io.roadmap.tasktracker.backend.security.*" \
  --tests "io.roadmap.tasktracker.backend.services.*" \
  --tests "io.roadmap.tasktracker.backend.kafka.*"
```

Один класс: `./gradlew test --tests "io.roadmap.tasktracker.backend.services.TasksServiceTest"`.
Один метод: добавить `.<метод>*`, например `...TasksServiceTest.findById_taskAbsent*`.
Принудительный перегон: `./gradlew cleanTest test`.

Покрыто: JWT (подпись/протухание/подделка), auth-менеджер, AuthService, TasksService
(владение/404/ошибки), OutboxRelay (SENT/WAITING/FAILED, retry×3), KafkaEventPublisher
(фикс глотания доставки), dead-broker семантика реального клиента. **51 тест.**

Отчёты: `build/reports/tests/test/index.html` (HTML), `build/test-results/test/TEST-*.xml` (JUnit XML).

---

## task-tracker-email-sender (Spring Boot 4.1, servlet)

```bash
cd task-tracker-email-sender && ./gradlew test \
  --tests "io.roadmap.tasktracker.emailsender.services.*" \
  --tests "io.roadmap.tasktracker.emailsender.kafka.*"
```

Покрыто: inbox-дедупликация (null/дубль/кривой UUID), EmailService (mock-режим vs реальная
отправка, fallback текста, проброс MailException), OutboxRelay (SENT/WAITING/FAILED,
root-cause, усечение 500), KafkaMessageListener (асимметрия обработчиков, report-письма
всегда в mock-режиме). **22 теста.** Всё синхронно — StepVerifier не используется.

Отчёты: те же `build/reports/tests/test/index.html`.

---

## task-tracker-scheduler (Spring Boot 4.1, WebFlux)

```bash
cd task-tracker-scheduler && ./gradlew test \
  --tests "io.roadmap.tasktracker.scheduler.kafka.*" \
  --tests "io.roadmap.tasktracker.scheduler.report.*" \
  --tests "io.roadmap.tasktracker.scheduler.job.*"
```

Покрыто: KafkaService (CORRELATION_ID-заголовок; пин того же swallow-бага, что был в бэке —
здесь НЕ исправлен), ReportGenerationService (correlation round-trip, обогащение email,
Jackson 3 unchecked-ошибки), ReportJobs (пагинация: страницы [1, 2] + остановка по границам,
зашитые даты, тик до подготовки → страница 0). **13 тестов.**

Отчёты: те же `build/reports/tests/test/index.html`.

---

## task-tracker-summarization-server (NestJS 11, Jest)

```bash
cd task-tracker-summarization-server && npm test          # один прогон
npm run test:watch                                        # watch-режим
npm run test:cov                                          # + покрытие
npx jest summarization.service.spec                       # один файл
npx jest -t "отправляет в GigaChat"                       # по имени теста
```

Покрыто: маппинг payload для промпта, парсинг/вырезание JSON-массива из ответа GigaChat,
формат запроса, SyntaxError-контракты, контроллер /dev/send (делегирование + глотание ошибок),
Kafka-консьюмер. **16 тестов, 3 suites.**

Вывод — прямо в консоль jest; JUnit-XML не генерируется (при необходимости:
`npm i -D jest-junit` + reporter).

### Нюансы, уже решённые в конфиге (не ломать)

- **Node ≥ 24.9 обязателен**: jest 30 требует нативный `require(esm)`. На машине —
  `~/.nvm/versions/node/v24.16.0`; в PATH по умолчанию Node 22 — hence `export PATH` в команде.
- **`@nestjs/config@12` — ESM-only**, jest его не ест нативно: в `package.json` уже добавлен
  отдельный ts-jest-трансформер для `.js` (`allowJs` + `commonjs`) и исключение в
  `transformIgnorePatterns` (`/node_modules/(?!@nestjs/config/)`).
- `app.controller.spec.ts` мокает `SummarizationService` целиком — не добавляйте `AppService`
  как провайдер: он тянет `KAFKA_CLIENT` + `ConfigService`.

---

## Известные грабли окружения

| Симптом | Причина | Лечение |
|---|---|---|
| `Server returned HTTP response code: 407` при `./gradlew` | враппер качает дистрибутив без прокси | прокси уже в `~/.gradle/gradle.properties`; не удаляйте его |
| `/bin/sh^M: bad interpreter` на `./gradlew` | CRLF-окончания скрипта | `sed -i 's/\r$//' gradlew` (уже сделано в email-sender) |
| `Must use import to load ES Module: @nestjs/config` | Node < 24.9 или затёрт jest-конфиг | Node 24 + не удалять `transformIgnorePatterns` из `package.json` |
| Контекст-тесты падают без фильтров | им нужны живые Postgres:5440 и Kafka:9092 | фильтры из этого гайда; либо Testcontainers (см. `task-tracker-backend/docs/testcontainers-guide.md`) |

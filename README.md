# Poly Price Watcher

`Spring Boot` сервис на `Java 17` для получения рыночных обновлений Polymarket по WebSocket, отслеживания изменения цены по нескольким рынкам и записи событий изменения цены в PostgreSQL.

## Что реализовано

- подключение к публичному `market` WebSocket каналу Polymarket
- подписка на 3+ заранее сконфигурированных `assetId`
- heartbeat `PING` и автоматический reconnect
- парсинг `book`, `best_bid_ask`, `price_change`, `last_trade_price`
- хранение последнего известного состояния цены в памяти
- запись событий изменения цены в таблицу `price_change_events`
- защита от stale/out-of-order событий по `timestamp + source priority`
- сериализация конкурентных обновлений по одному `assetId` внутри одного инстанса
- Flyway-миграции, тестовый профиль на `H2`, health endpoint и CI на `mvn test`

## Стек

- `Java 17`
- `Spring Boot 3`
- `Spring Data JPA`
- `Spring WebFlux`
- `Flyway`
- `PostgreSQL`
- `JUnit 5`
- `Mockito`

## Структура

- `src/main/java` — основной код
- `src/main/resources/application.yml` — общая конфигурация
- `src/main/resources/application-local.yml` — локальный профиль
- `src/main/resources/application-prod.yml` — prod-профиль через env vars
- `src/main/resources/polymarket-markets.yml` — статический список отслеживаемых рынков
- `src/main/resources/db/migration` — Flyway SQL-миграции
- `src/test/resources/application-test.yml` — тестовый профиль
- `.github/workflows/ci.yml` — GitHub Actions pipeline с `mvn test`

## Архитектура

- `config` — `@ConfigurationProperties` и runtime-параметры
- `market.service` — загрузка и валидация списка рынков
- `market.websocket` — WebSocket-клиент, парсер и обработчик рыночных событий
- `price.service` — бизнес-логика обновления цены, stale-filtering и запись изменений
- `price.store` — текущее состояние последней цены
- `price.repository` / `price.entity` — persistence layer

Текущая политика определения изменения цены:

- более старое событие игнорируется
- событие с тем же `timestamp`, но с более слабым источником, игнорируется
- приоритет источников:
  `BEST_BID_ASK > ORDER_BOOK > PRICE_CHANGE > LAST_TRADE`
- в PostgreSQL пишется только реальное изменение цены

## Быстрый запуск

Есть два варианта запуска.

### Вариант 1. Полностью через Docker

1. Поднять весь стек:

```bash
docker compose up -d --build
```

2. Проверить health:

```bash
curl http://localhost:8086/actuator/health
```

### Вариант 2. PostgreSQL в Docker + приложение локально

1. Поднять только PostgreSQL:

```bash
docker compose up -d postgres
```

2. При необходимости отредактировать список рынков в `src/main/resources/polymarket-markets.yml`.

3. Запустить приложение локально:

```bash
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

4. Проверить health:

```bash
curl http://localhost:8086/actuator/health
```

Ожидаемый ответ:

```json
{"status":"UP"}
```

После полного docker-запуска логи приложения можно посмотреть так:

```bash
docker logs -f poly-price-watcher-app
```

## Проверка результата

Если Polymarket присылает апдейты по выбранным рынкам, в логах будут сообщения вида:

```text
Tracked price changed: assetId=..., marketId=..., price=..., source=..., eventId=...
```

Посмотреть накопленные события в PostgreSQL:

```bash
docker exec -it poly-price-watcher-postgres psql -U poly_price_watcher -d poly_price_watcher
```

```sql
select id,
       asset_id,
       market_id,
       previous_price,
       current_price,
       price_source,
       created_at
from price_change_events
order by id desc
limit 20;
```

## Конфигурация

Подход к конфигурации:

- `application.yml` — только общие настройки Spring Boot
- `application-local.yml` — локальная разработка
- `application-prod.yml` — деплой через переменные окружения
- `application-test.yml` — изолированные тесты

Основные runtime-параметры:

- `SPRING_PROFILES_ACTIVE` — `local` или `prod`
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SERVER_PORT`
- `POLYMARKET_WEBSOCKET_ENABLED`
- `POLYMARKET_WEBSOCKET_URL`
- `POLYMARKET_WEBSOCKET_PING_INTERVAL`
- `POLYMARKET_WEBSOCKET_RECONNECT_DELAY`
- `POLYMARKET_WEBSOCKET_CUSTOM_FEATURE_ENABLED`

Рынки задаются в:

- `src/main/resources/polymarket-markets.yml`

Для каждого рынка сейчас задаются:

- `asset-id`
- `market-id`
- `condition-id`
- `market-slug`
- `market-question`
- `outcome`

Для текущей реализации WebSocket-подписки реально обязателен только `asset-id`.
Остальные поля сейчас используются как metadata для логов и записей в БД, а также оставлены как задел на будущее.

Если sample markets уже `resolved` или `inactive`, замените их на актуальные active markets с новыми `asset-id`.

Сервис валидирует:

- минимум 3 рынка
- отсутствие дублей по `assetId`
- отсутствие дублей по `marketId`
- отсутствие дублей по `marketSlug`

Важно:

- для public `market` WebSocket ключи доступа не нужны
- текущий `PriceStateStore` — in-memory и рассчитан на single-instance запуск

## Локальная разработка

Шаблон переменных окружения:

- `.env.example`

Локальный профиль уже содержит готовые значения для:

- PostgreSQL на `localhost:5434`
- порта приложения `8086`
- публичного WebSocket Polymarket

## Тесты и CI

Локально:

```bash
mvn test
```

Сейчас тестами покрыты:

- загрузка и валидация конфигурации рынков
- парсинг WebSocket-сообщений
- маршрутизация событий в price layer
- логика stale-filtering и source priority
- запись `price_change_events` через Flyway + JPA

В GitHub Actions настроен базовый pipeline, который запускает `mvn test` на каждый `push` и `pull_request`.

## Ограничения текущей версии

- список рынков пока статический, без автоподбора через Gamma API
- текущее состояние цены хранится только в памяти одного инстанса
- live-подключение к реальному Polymarket WebSocket в тестах не эмулируется

## Возможные улучшения

- добавить auto-discovery или периодическое обновление активных рынков через Gamma API вместо полностью статического списка
- добавить watchdog на "молчаливое зависание" потока market data, когда WebSocket соединение открыто, но реальные рыночные события долго не приходят
- вынести `PriceStateStore` во внешний storage, например Redis, если понадобится multi-instance deployment
- добавить интеграционный тест WebSocket-клиента против локального test WebSocket server
- добавить отдельную метрику или health-индикатор на возраст последнего market data события
- при необходимости упростить конфиг tracked markets до минимального набора полей и подгружать остальную metadata динамически

## Полезные источники

- [Polymarket WebSocket Overview](https://docs.polymarket.com/market-data/websocket/overview)
- [Polymarket Market Channel](https://docs.polymarket.com/api-reference/wss/market)
- [Polymarket Introduction](https://docs.polymarket.com/api-reference/introduction)

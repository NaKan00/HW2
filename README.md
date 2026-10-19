# HW2 — Marketplace OpenAPI + CRUD

Учебный REST API: товары, заказы с резервированием остатков, промокоды и JWT.
Стек: Java 21, Spring Boot 3.5, Maven, PostgreSQL 17, Flyway, Spring Data JPA,
OpenAPI Generator и Docker Compose. Денежные расчёты используют `BigDecimal`.

## Быстрый запуск

Нужны Docker Engine и Docker Compose v2 (на Windows — Docker Desktop с Linux-контейнерами).
Команды Compose одинаковы в Windows PowerShell и Unix. Из корня репозитория:

```sh
docker compose up --build
```

API слушает порт **8080**. PostgreSQL хранит данные в именованном томе и не
публикует свой порт на хост. Приложение ждёт успешного `pg_isready`, выполняет
миграции и проверяет схему через Hibernate `ddl-auto: validate`.
Оба сервиса имеют healthcheck; проверка приложения авторизуется и обращается
к `/actuator/health`, который также проверяет соединение с БД.

```sh
docker compose ps
docker compose logs -f application
docker compose down             # остановить, сохранив данные
docker compose down -v          # удалить и учебную базу данных
```

Значения по умолчанию предназначены для локальной демонстрации. Для изменения
скопируйте `.env.example` в `.env`. Перед использованием вне локальной машины
задайте собственные `DB_PASSWORD`, `JWT_SECRET` (не менее 32 байт), `ADMIN_EMAIL`
и `ADMIN_PASSWORD`. Секреты не добавляйте в Git.

| Переменная | Значение по умолчанию | Назначение |
|---|---|---|
| `JWT_ACCESS_MINUTES` | 20 | Срок access JWT в минутах |
| `JWT_REFRESH_DAYS` | 14 | Срок refresh JWT в днях |
| `ORDER_RATE_LIMIT_MINUTES` | 1 | Раздельный интервал CREATE_ORDER и UPDATE_ORDER; 0 отключает |
| `ADMIN_EMAIL` | admin@example.com | Учебный ADMIN, создаваемый при первом запуске Compose |
| `ADMIN_PASSWORD` | AdminDemo123! | Пароль учебного ADMIN; смена переменной не меняет уже сохранённый пароль |

## Сборка и тесты

Для сборки без контейнера приложения нужны **полный JDK 21**, Maven 3.9+ и
работающий Docker для Testcontainers:

```sh
mvn clean package
```

Эта команда заново генерирует интерфейсы и DTO, компилирует проект и запускает
тесты с настоящим PostgreSQL в отдельном одноразовом контейнере. H2 не используется.
`mvn test` запускает тесты без очистки. Образ приложения собирается с
`-DskipTests`; для проверки тестов используйте команды выше отдельно.

Запуск JAR с уже работающей PostgreSQL, Windows PowerShell:

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/marketplace"
$env:DB_USER = "marketplace"
$env:DB_PASSWORD = "marketplace"
$env:JWT_SECRET = "replace-with-your-own-random-key-at-least-32-bytes"
java -jar target/marketplace-1.0.0.jar
```

Unix:

```sh
export DB_URL=jdbc:postgresql://localhost:5432/marketplace
export DB_USER=marketplace
export DB_PASSWORD=marketplace
export JWT_SECRET='replace-with-your-own-random-key-at-least-32-bytes'
java -jar target/marketplace-1.0.0.jar
```

При таком запуске ADMIN необязателен: создайте его, задав одновременно
`ADMIN_EMAIL` и `ADMIN_PASSWORD` перед стартом. Публичная регистрация ADMIN запрещена.

## Контракт и структура

Единственный источник интерфейсов и моделей запросов/ответов:
[`src/main/resources/openapi/openapi.yaml`](src/main/resources/openapi/openapi.yaml).
Maven OpenAPI Generator выполняется на фазе `generate-sources`, генерирует
Spring-интерфейсы и модели с Jakarta Validation в
`target/generated-sources/openapi`. Каталог `target/` игнорируется Git.
Контроллеры реализуют эти интерфейсы и используют только сгенерированные DTO.
После изменения контракта выполните `mvn clean package`; генерируемые файлы вручную не редактируются.

```text
src/main/java/ru/hw/marketplace/
  controller/  реализация сгенерированных API, дополнительные проверки входных данных
  service/     бизнес-логика, транзакции, преобразование сущностей в DTO
  repository/  JPA-запросы и блокировки
  entity/      сущности хранения
  security/    JWT, текущий пользователь
  exception/   единый контракт ошибок
  logging/     JSON-журнал запросов с маскированием
  config/      Spring Security, начальная учётная запись ADMIN
src/main/resources/db/migration/  схема Flyway
src/test/java/                   интеграционные тесты и тесты журналирования
scripts/                         сценарии защиты и просмотр БД
```

## API и роли

Все бизнес-эндпоинты и `/actuator/health` требуют заголовок
`Authorization: Bearer <access_token>`. Открыты только три `/auth/*` операции.

| Операция | USER | SELLER | ADMIN |
|---|---|---|---|
| `GET /products`, `GET /products/{id}` | да | да | да |
| `POST /products` | нет | свой товар | да |
| `PUT /products/{id}`, `DELETE /products/{id}` | нет | только свой | любой |
| `POST /orders` | свой заказ | нет | свой заказ |
| `GET /orders/{id}`, `PUT /orders/{id}`, `POST /orders/{id}/cancel` | только свой | нет | любой |
| `POST /orders/{id}/status` | нет | нет | следующий шаг жизненного цикла |
| `POST /promo-codes` | нет | да | да |

`seller_id` и `user_id` берутся из JWT, а не из тела запроса. При работе ADMIN
с чужим заказом UPDATE_ORDER учитывается у владельца заказа.
Пароли хешируются BCrypt. Refresh JWT имеет отдельное назначение, не принимается
как access JWT и проверяется через `/auth/refresh`. Refresh-токены действуют до
истечения своего срока; одноразовая ротация и logout в это задание не входят.

Пример регистрации и входа в Windows PowerShell (также можно использовать `scripts/demo.py`):

```powershell
$base = "http://localhost:8080"
$registration = @{ email = "user@example.com"; password = "DemoPassword123!"; role = "USER" } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$base/auth/register" -ContentType "application/json" -Body $registration
$credentials = @{ email = "user@example.com"; password = "DemoPassword123!" } | ConvertTo-Json
$tokens = Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType "application/json" -Body $credentials
$headers = @{ Authorization = "Bearer $($tokens.access_token)" }
Invoke-RestMethod -Uri "$base/products" -Headers $headers
$refresh = @{ refresh_token = $tokens.refresh_token } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$base/auth/refresh" -ContentType "application/json" -Body $refresh
```

Unix:

```sh
curl -i -X POST localhost:8080/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"user@example.com","password":"DemoPassword123!","role":"USER"}'
curl -i -X POST localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"user@example.com","password":"DemoPassword123!"}'
# Скопируйте access_token из ответа:
export TOKEN='<access_token>'
curl -i localhost:8080/products -H "Authorization: Bearer $TOKEN"
curl -i -X POST localhost:8080/auth/refresh \
  -H 'Content-Type: application/json' -d '{"refresh_token":"<refresh_token>"}'
```

Товар создаётся/обновляется полным телом: `name`, `price`, `stock`, `category`,
`status` обязательны, `description` необязателен. Цена имеет максимум два
десятичных знака. Статусы: ACTIVE, INACTIVE, ARCHIVED.
`DELETE` лишь присваивает ARCHIVED; запись остаётся доступна через GET и SELECT.

`GET /products?page=0&size=20&status=ACTIVE&category=Books` возвращает
`items`, `totalElements`, `page`, `size`. Категория сравнивается целиком,
с учётом регистра. Размер страницы: 1–100, по умолчанию 20; нумерация с 0.
Архивные товары видны в общем списке, если явно не задан фильтр статуса.

Создание заказа: `{"items":[{"product_id":"<uuid>","quantity":2}],"promo_code":"SALE10"}`.
Обновление: то же `items`, без нового промокода. Допускается 1–50 непустых
позиций, количество 1–999, повторяющиеся product_id отклоняются.
Строки кода соответствуют `^[A-Z0-9_]{4,20}$`.
Точные поля создания промокода приведены в OpenAPI и `scripts/demo.py`.

## Транзакции и жизненный цикл

Flyway создаёт шесть таблиц, внешние ключи, CHECK-ограничения, уникальные
email/коды/позиции заказа, частичный уникальный индекс одного активного заказа
на пользователя и индекс `products.status`. Даты создаются автоматически;
`updated_at` также поддерживается триггерами PostgreSQL.

Создание заказа выполняет: лимит CREATE_ORDER → проверка активного заказа →
проверка всех товаров и статусов → проверка всех остатков → резерв → снимки цен →
промокод/суммы → запись операции. Недостаток остатков перечисляет все проблемные
позиции. Любая ошибка откатывает заказ, остатки, промокод и операцию вместе.

Изменения заказов сериализуются блокировкой строки владельца. Товары блокируются
`SELECT ... FOR UPDATE` в порядке UUID; промокод тоже блокируется. Это предотвращает
перепродажу остатков, гонки лимитов и превышение числа использований промокода.
Транзакции обновления/отмены используют тот же порядок блокировок.
Цена сохраняется в `order_items.price_at_order`, поэтому изменение карточки
не меняет старый заказ. При замене позиций создаются новые снимки цен.

Обновление разрешено только для CREATED: возвращаются старые остатки,
резервируются новые, позиции заменяются, суммы пересчитываются. Промокод
проверяется заново; собственное использование заказа учитывается при max_uses.
Если новая сумма ниже минимума, промокод снимается и его использование освобождается.
Неактивный/просроченный промокод вызывает ошибку и полный откат обновления.
При отмене остатки и использование промокода возвращаются один раз.

PERCENTAGE: скидка ограничена 70% подытога (предел округляется вниз до копеек).
FIXED_AMOUNT: скидка не больше подытога. Суммы имеют два десятичных знака.

```text
CREATED -> PAYMENT_PENDING -> PAID -> SHIPPED -> COMPLETED
   |              |
   +-> CANCELED <-+
```

ADMIN продвигает заказ по одному шагу через
`POST /orders/{id}/status` с телом `{"status":"PAYMENT_PENDING"}`.
Отмена выполняется исключительно через `POST /orders/{id}/cancel`.
Пропуски, обратные переходы и изменение конечных состояний запрещены.

## Демонстрация и SELECT

После запуска Compose нужен Python 3 (сторонние библиотеки не нужны).
Windows PowerShell, из корня репозитория:

```powershell
py scripts/demo.py
py scripts/negative.py
```

Оба сценария работают без sh, Bash, WSL и Git Bash. `demo.py` вызывает
`docker compose exec` напрямую и передаёт SELECT через стандартный ввод `psql`.
Docker Desktop должен быть запущен, команда `docker` — доступна в PATH.

Unix:

```sh
python3 scripts/demo.py
python3 scripts/negative.py
# Дополнительный просмотр БД для Unix:
sh scripts/db-inspect.sh
```

`demo.py` создаёт новые USER/SELLER, выполняет вход, создание/список/чтение/
обновление товара, создание промокода и заказа, SELECT по всем таблицам,
обновление/отмену заказа, проверку восстановленного остатка и мягкое удаление.
После удаления SELECT показывает сохранённый ARCHIVED товар.
Повторный запуск использует новые email и коды.
`--skip-sql` пропускает только SELECT, если API запущен без Compose.

`negative.py` проверяет валидацию, отсутствующий/неактивный товар, недостаток
остатков, неправильный промокод и минимальную сумму, чужой заказ, лимит,
активный заказ, неправильный переход, отсутствие/истечение/повреждение JWT,
недопустимый refresh и запрещённую роль. Для демонстрации ORDER_HAS_ACTIVE
скрипт ждёт истечения лимита (по умолчанию 61 секунду).
Если изменены `ORDER_RATE_LIMIT_MINUTES` или `JWT_SECRET`, передайте те же
значения в окружение скрипта. Для демонстрации обеих ошибок лимита интервал должен быть больше 0.
Токены и пароли сценарии не печатают.

Например, если настройки были изменены в `.env`, в PowerShell задайте
`$env:ORDER_RATE_LIMIT_MINUTES = "2"` и `$env:JWT_SECRET = "<тот же ключ, что в .env>"`
перед запуском сценария; в Unix используйте `export ИМЯ=значение`.
Сам файл `.env` не экспортирует переменные в Python.

Прямые SELECT из Windows PowerShell (также работают в Unix):

```powershell
docker compose exec -T postgres psql -U marketplace -d marketplace -v ON_ERROR_STOP=1 -c "SELECT id, email, role, created_at, updated_at FROM users;"
docker compose exec -T postgres psql -U marketplace -d marketplace -v ON_ERROR_STOP=1 -c "SELECT id, name, stock, price, status, seller_id FROM products;"
docker compose exec -T postgres psql -U marketplace -d marketplace -v ON_ERROR_STOP=1 -c "SELECT id, user_id, status, subtotal, discount_amount, total_amount, promo_code_id FROM orders;"
docker compose exec -T postgres psql -U marketplace -d marketplace -v ON_ERROR_STOP=1 -c "SELECT order_id, product_id, quantity, price_at_order FROM order_items;"
docker compose exec -T postgres psql -U marketplace -d marketplace -v ON_ERROR_STOP=1 -c "SELECT code, discount_type, discount_value, min_order_amount, current_uses, max_uses, active FROM promo_codes;"
docker compose exec -T postgres psql -U marketplace -d marketplace -v ON_ERROR_STOP=1 -c "SELECT user_id, operation_type, created_at FROM user_operations ORDER BY created_at;"
docker compose exec -T postgres psql -U marketplace -d marketplace -v ON_ERROR_STOP=1 -c "SELECT id, name, status FROM products WHERE status = 'ARCHIVED';"
```

Для интерактивной проверки в любой системе:

```sh
docker compose exec postgres psql -U marketplace -d marketplace
```

```sql
SELECT id, email, role, created_at, updated_at FROM users;
SELECT id, name, stock, price, status, seller_id FROM products;
SELECT id, user_id, status, subtotal, discount_amount, total_amount, promo_code_id FROM orders;
SELECT order_id, product_id, quantity, price_at_order FROM order_items;
SELECT code, discount_type, discount_value, min_order_amount, current_uses, max_uses, active FROM promo_codes;
SELECT user_id, operation_type, created_at FROM user_operations ORDER BY created_at;
SELECT id, name, status FROM products WHERE status = 'ARCHIVED';
```

## Ошибки и журнал

Все API-ошибки имеют один JSON-контракт (details необязателен):

```json
{"error_code":"VALIDATION_ERROR","message":"Request validation failed","details":{"violations":[{"field":"name","violation":"size must be between 1 and 255"}]}}
```

```json
{"error_code":"INSUFFICIENT_STOCK","message":"Insufficient stock","details":{"products":[{"product_id":"11111111-1111-1111-1111-111111111111","requested_quantity":5,"available_stock":2}]}}
```

| HTTP | Коды |
|---|---|
| 400 | VALIDATION_ERROR |
| 401 | TOKEN_INVALID, TOKEN_EXPIRED, REFRESH_TOKEN_INVALID |
| 403 | ACCESS_DENIED, ORDER_OWNERSHIP_VIOLATION |
| 404 | PRODUCT_NOT_FOUND, ORDER_NOT_FOUND |
| 409 | PRODUCT_INACTIVE, ORDER_HAS_ACTIVE, INVALID_STATE_TRANSITION, INSUFFICIENT_STOCK |
| 422 | PROMO_CODE_INVALID, PROMO_CODE_MIN_AMOUNT |
| 429 | ORDER_LIMIT_EXCEEDED |
| 500 | INTERNAL_ERROR (неожиданная внутренняя ошибка без утечки подробностей) |

Каждый запрос записывается отдельной JSON-строкой: request_id, method, endpoint,
status_code, duration_ms, user_id (null до авторизации), timestamp. Ответ содержит
`X-Request-Id`. Для POST/PUT/DELETE журналируется тело (до 16 KiB); поля паролей,
токенов и секретов маскируются рекурсивно. Неразбираемое/усечённое тело скрывается целиком.
Authorization и тела ответов не журналируются.

| № | Где реализовано |
|---|---|
| 1. OpenAPI CRUD | [openapi.yaml](src/main/resources/openapi/openapi.yaml): операции `/products`; [ProductController.java](src/main/java/ru/hw/marketplace/controller/ProductController.java): реализация API |
| 2. OpenAPI data schemas | [openapi.yaml](src/main/resources/openapi/openapi.yaml): ProductCreate, ProductUpdate, ProductResponse, ProductPage, модели заказов, промокодов, авторизации и ошибок |
| 3. OpenAPI code generation | [pom.xml](pom.xml): плагин на фазе generate-sources; [ProductController.java](src/main/java/ru/hw/marketplace/controller/ProductController.java) использует интерфейсы и DTO; [.gitignore](.gitignore) исключает target/ |
| 4. PostgreSQL + basic CRUD | [V1__marketplace.sql](src/main/resources/db/migration/V1__marketplace.sql), [application.yaml](src/main/resources/application.yaml), [ProductRepository.java](src/main/java/ru/hw/marketplace/repository/ProductRepository.java), [ProductService.java](src/main/java/ru/hw/marketplace/service/ProductService.java): хранение, пагинация, точный фильтр и мягкое удаление |
| 5. Contract error handling | [openapi.yaml](src/main/resources/openapi/openapi.yaml): ApiError/ErrorCode; [ApiExceptionHandler.java](src/main/java/ru/hw/marketplace/exception/ApiExceptionHandler.java), [Errors.java](src/main/java/ru/hw/marketplace/exception/Errors.java), [SecurityConfig.java](src/main/java/ru/hw/marketplace/config/SecurityConfig.java), [JwtFilter.java](src/main/java/ru/hw/marketplace/security/JwtFilter.java): единый JSON ошибок |
| 6. Contract validation | [openapi.yaml](src/main/resources/openapi/openapi.yaml): Jakarta Validation для DTO; [ContractChecks.java](src/main/java/ru/hw/marketplace/controller/ContractChecks.java), [PromoController.java](src/main/java/ru/hw/marketplace/controller/PromoController.java): дополнительные входные проверки; [MarketplaceIntegrationTest.java](src/test/java/ru/hw/marketplace/MarketplaceIntegrationTest.java) |
| 7. Complex order business logic | [OrderService.java](src/main/java/ru/hw/marketplace/service/OrderService.java), [PromoService.java](src/main/java/ru/hw/marketplace/service/PromoService.java), [ProductRepository.java](src/main/java/ru/hw/marketplace/repository/ProductRepository.java), [PromoRepository.java](src/main/java/ru/hw/marketplace/repository/PromoRepository.java): транзакции, блокировки, лимиты, снимки цен, скидки и переходы |
| 8. JSON API logging | [ApiLoggingFilter.java](src/main/java/ru/hw/marketplace/logging/ApiLoggingFilter.java), [logback-spring.xml](src/main/resources/logback-spring.xml), [ApiLoggingFilterTest.java](src/test/java/ru/hw/marketplace/ApiLoggingFilterTest.java): JSON, X-Request-Id, маскирование |
| 9. JWT authentication | [AuthController.java](src/main/java/ru/hw/marketplace/controller/AuthController.java), [AuthService.java](src/main/java/ru/hw/marketplace/service/AuthService.java), [JwtService.java](src/main/java/ru/hw/marketplace/security/JwtService.java), [JwtFilter.java](src/main/java/ru/hw/marketplace/security/JwtFilter.java): BCrypt, access/refresh и сроки действия |
| 10. Role-based access | [SecurityConfig.java](src/main/java/ru/hw/marketplace/config/SecurityConfig.java), [ProductController.java](src/main/java/ru/hw/marketplace/controller/ProductController.java), [OrderController.java](src/main/java/ru/hw/marketplace/controller/OrderController.java), [PromoController.java](src/main/java/ru/hw/marketplace/controller/PromoController.java), [ProductService.java](src/main/java/ru/hw/marketplace/service/ProductService.java), [OrderService.java](src/main/java/ru/hw/marketplace/service/OrderService.java): @PreAuthorize и проверки владельца |

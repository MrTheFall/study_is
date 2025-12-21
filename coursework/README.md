# Krusty Crab — Coursework (UC‑1…UC‑17)

Проект: backend (Spring Boot) + frontend (React/Vite) + Postgres (Docker).

## Быстрый старт

### 1) База данных (Postgres в Docker)

Запуск Postgres:
- `docker compose -f docker/docker-compose.yml up -d`

Создание схемы/функций:
- `./scripts/create_db.sh`

Сидирование тестовых данных:
- `./scripts/seed_db.sh`

Применить версионированные миграции (без пересоздания БД):
- `./scripts/migrate_db.sh`

Обновить только функции (после правок `db/functions.sql`):
- `./scripts/update_functions.sh`

### 2) Backend

- `cd backend`
- `./gradlew bootRun`

Backend порт: `13228`  
Swagger UI: `http://localhost:13228/swagger-ui.html`

### 3) Frontend

- `cd frontend`
- `npm install --legacy-peer-deps`
- `npm run dev`

Frontend: `http://localhost:5173`

## Тесты

Backend (все тесты, включая интеграционные через Testcontainers):
- `cd backend && ./gradlew test`

Backend без интеграционных тестов (если нет Docker):
- `cd backend && ./gradlew test -PexcludeIntegration`

## Учётные данные (seed)

Из `db/seed.sql`:
- Manager: `manager` / `Manager123!`
- Cashier: `cashier` / `Cashier123!`
- Cook: `cook` / `Cook123!`
- Client: `client@example.com` / `Client123!`

## Переменные окружения

Backend:
- `JWT_SECRET` — секрет JWT (по умолчанию задан в `backend/src/main/resources/application.properties`)
- `FRONTEND_URL` — origins для CORS (по умолчанию: `http://localhost:5173,http://ubuntu:5173`)

Frontend:
- `VITE_API_PORT` — порт backend (по умолчанию `13228`)
- `VITE_API_BASE_URL` — полный base URL backend (если нужно переопределить)

## Генерация API клиента (frontend)

После изменений `backend/src/main/resources/openapi.yml`:
- `cd frontend && npm run generate-api`

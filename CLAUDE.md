# QueueTable — Contexto del Proyecto

## Qué es
SaaS B2B para restaurantes que unifica cola online, reservas y gestión de mesas en tiempo real. El cliente escanea un QR, entra en la cola desde el móvil. El restaurante gestiona todo desde un panel.

## Estado actual
- **Backend MVP**: COMPLETADO (7 sprints)
- **Frontend**: Pendiente (React / Next.js, repo separado)
- **PRD completo**: `PRD.md` (fuente de verdad de requisitos y arquitectura)

## Stack
- **Backend**: Spring Boot 3.4.4 / Java 21 / Maven
- **Frontend**: React / Next.js (no iniciado)
- **DB**: PostgreSQL via Supabase (managed). Dev local: Docker Compose en puerto 5433
- **Auth**: JWT + refresh tokens (jjwt 0.12.6)
- **Realtime**: WebSockets STOMP + SockJS (staff), SSE (cliente)
- **QR**: ZXing 3.5.3
- **Docs**: OpenAPI / Swagger UI (springdoc 2.8.13)
- **Tests**: JUnit 5 + Testcontainers + MockMvc (77+ tests)
- **Migrations**: Flyway (V1-V6)

## Arquitectura backend
Screaming + Hexagonal en `queuetable-backend/`:
```
com.queuetable/
├── auth/          (register, login, refresh — JWT)
├── restaurant/    (CRUD + QR code)
├── staff/         (entity + repository)
├── table/         (CRUD + state transitions + reserved_soon + getAvailableTables)
├── queue/         (join, cancel, notify, confirm, skip, seat, walk-in, SSE)
├── reservation/   (CRUD + arrive, seat, complete, no-show, cancel)
├── config/        (RestaurantConfig — timeouts, durations)
└── shared/        (security, exceptions, audit, websocket, scheduling, events)
```

## Endpoints implementados

### Auth
- `POST /auth/register`, `/auth/login`, `/auth/refresh`

### Restaurant
- `GET/PATCH /restaurants/{id}`, `GET /restaurants/{id}/qr`
- `GET/PATCH /restaurants/{id}/config`

### Tables (staff, JWT)
- `GET /restaurants/{id}/tables` (incluye flag `reservedSoon`)
- `GET /restaurants/{id}/tables/available?groupSize=N`
- `POST /restaurants/{id}/tables`
- `PATCH /tables/{id}`, `PATCH /tables/{id}/status`, `DELETE /tables/{id}`

### Queue — public (sin auth)
- `GET /public/restaurants/{slug}` — info pública
- `GET /public/restaurants/{slug}/queue/status` — preview cola
- `POST /public/restaurants/{slug}/queue` — unirse
- `GET /public/queue/{entryId}?accessToken=` — tracking
- `POST /public/queue/{entryId}/confirm` — confirmar asistencia
- `DELETE /public/queue/{entryId}?accessToken=` — cancelar
- `GET /public/queue/{entryId}/events?accessToken=` — SSE stream

### Queue — staff (JWT)
- `GET /restaurants/{id}/queue` (filtrable por status)
- `POST /restaurants/{id}/queue` — walk-in
- `POST /restaurants/{id}/queue/{entryId}/notify`
- `POST /restaurants/{id}/queue/{entryId}/seat`
- `POST /restaurants/{id}/queue/{entryId}/skip`
- `POST /restaurants/{id}/queue/{entryId}/cancel`

### Reservations (staff, JWT)
- `GET /restaurants/{id}/reservations` (filtro status/date)
- `POST /restaurants/{id}/reservations`
- `PATCH /reservations/{id}`
- `POST /reservations/{id}/arrive`
- `POST /reservations/{id}/seat`
- `POST /reservations/{id}/complete`
- `POST /reservations/{id}/no-show`
- `POST /reservations/{id}/cancel`

### WebSocket
- `/ws` — STOMP + SockJS, topics: `queue.updated`, `table.updated`, `reservation.updated`

## Features de robustez
- Optimistic locking (`@Version`) en todas las entidades mutables → 409 Conflict
- Rate limiting en POST /public/**/queue (10 joins/IP/hora) → 429
- Cron cada 60s: expira entries sin confirmar + marca no-show en reservas
- Audit trail: log estructurado en todas las transiciones de estado
- Queue advance automático al cancelar reserva o liberar mesa

## Decisiones clave
- MVP = 1 cuenta = 1 restaurante (no multi-tenant)
- Cliente sin cuenta (token UUID anónimo en localStorage)
- Roles ADMIN/STAFF con mismos permisos en MVP
- Sin entidad Visit en MVP (implícita en estados de mesa)
- Sin assignment-engine (staff asigna manualmente con filtros simples)
- Notificaciones solo visuales en web (no SMS/WhatsApp/push)

## Roadmap (7 sprints)
1. ~~Fundación~~ ✅ (auth, restaurant, tables, config, QR, DB)
2. ~~Cola del cliente~~ ✅ (página pública, QR flow, tracking, panel básico)
3. ~~Reservas + WebSocket~~ ✅ (CRUD reservas, STOMP para staff)
4. ~~Mesas + asignación~~ ✅ (reserved_soon, getAvailableTables, walk-in, SSE)
5. ~~Lógica combinada~~ ✅ (notify/confirm/skip, queue advance)
6. ~~Robustez~~ ✅ (cron expiraciones, audit logging, rate limiting)
7. ~~Pulido y lanzamiento~~ ✅ (E2E tests, Dockerfile, reservation complete)

## Repos
- Backend: `queuetable-backend/` → https://github.com/DanielRuiz-14/gestorColas.git
- Frontend: Pendiente (repo separado)

## Cómo correr
```bash
cd queuetable-backend
docker compose up -d          # PostgreSQL en puerto 5433
mvn spring-boot:run           # Backend en puerto 8080
mvn test                      # Integration tests
```

## Swagger UI
```
http://localhost:8080/swagger-ui.html
```

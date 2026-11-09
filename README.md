# URL Shortener

A Java 21 and Spring Boot service for creating short links, redirecting to their destinations, and viewing usage statistics. URLs are validated before storage, persisted in MySQL, cached in Redis with expiration metadata, and protected by a Redis-backed per-IP creation limit.

## Features

- **Base62 short codes** generated from the persisted URL record ID using the alphabet `0-9`, `A-Z`, and `a-z`.
- **URL validation** accepts absolute HTTP and HTTPS URLs with a host. Blank, malformed, and other-scheme URLs are rejected.
- **MySQL persistence** stores the short code, destination, click count, creation time, and expiration time.
- **Redis URL cache** stores the destination and expiration timestamp. Cache entries expire at the URL's expiration time; stale or invalid entries are evicted.
- **Atomic click tracking** increments the database count in a conditional update that only succeeds while the URL is active.
- **IP-based rate limiting** uses an atomic Redis Lua script and a fixed window. The default limit is 10 creation requests per client IP per 60 seconds.
- **URL expiration and cleanup**: new links expire after 30 days by default. A scheduled job deletes expired rows daily at midnight.
- **URL statistics** include the destination, click count, creation time, and expiration time.
- **REST API documentation** is generated with Springdoc OpenAPI and Swagger UI.

## Technology stack

| Area | Technology |
| --- | --- |
| Language and runtime | Java 21 |
| Application framework | Spring Boot 3.5.16, Spring MVC |
| Persistence | Spring Data JPA, Hibernate, MySQL 8 |
| Cache and rate limiting | Spring Data Redis, Redis 7 |
| API documentation | Springdoc OpenAPI 2.10.0, Swagger UI |
| Build | Maven Wrapper |
| Containers | Docker, Docker Compose |
| Tests | JUnit, Spring Boot Test, H2 (for database tests) |

## Architecture

```mermaid
flowchart LR
    Client[HTTP client or browser] -->|POST /shorten| App[Spring Boot REST API]
    Client -->|GET /{code}| App
    Client -->|GET /stats/{code}| App
    App -->|validate and rate limit| Redis[(Redis 7)]
    App -->|persist URL and metadata| MySQL[(MySQL 8)]
    App -->|cache destination and expiry| Redis
    App -->|302 Location: destination| Client
    Scheduler[Daily cleanup job] --> App
    App -->|delete expired URL rows| MySQL
    Swagger[Swagger UI / OpenAPI] --> App
```

### Request flows

1. **Create a link:** `POST /shorten` validates the HTTP(S) URL, checks the caller's Redis rate-limit counter, persists the URL, converts its generated database ID to Base62, and returns the short code and URL.
2. **Redirect:** `GET /{code}` checks Redis first, then MySQL on a cache miss. If the URL is active, the service atomically increments its click count, updates the cache when needed, and returns HTTP 302 with a `Location` header. Unknown codes return 404; expired links return 410.
3. **Read statistics:** `GET /stats/{code}` reads URL metadata from MySQL and returns the current statistics. It returns 404 for an unknown code and 410 for an expired code.
4. **Cleanup:** a scheduled job runs at `00:00` according to the application server's local time and removes expired rows (and legacy rows without an expiry once they are older than the configured retention cutoff).

## API

Base URL for local development: `http://localhost:8080`.

### `POST /shorten`

Create a short link. The request body must contain a non-blank absolute HTTP or HTTPS URL with a host.

Request:

```http
POST /shorten HTTP/1.1
Content-Type: application/json

{
  "longUrl": "https://example.com/articles/spring"
}
```

Successful response (`201 Created`):

```json
{
  "shortCode": "aZ19xQ",
  "shortUrl": "http://localhost:8080/aZ19xQ"
}
```

Relevant errors:

| Status | Meaning |
| --- | --- |
| `400 Bad Request` | Missing/blank URL, invalid URL, or malformed JSON |
| `429 Too Many Requests` | Client IP exceeded the configured creation limit |

Error body example:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "longUrl must be a valid HTTP or HTTPS URL"
}
```

### `GET /{code}`

Redirect to the original URL. A successful lookup returns `302 Found` with a `Location` header and no response body.

```http
GET /aZ19xQ HTTP/1.1
```

Relevant errors: `404 Not Found` for an unknown code and `410 Gone` for an expired link. A browser follows the redirect automatically; use `curl -i http://localhost:8080/aZ19xQ` to inspect the 302 response and its `Location` header.

### `GET /stats/{code}`

Retrieve the URL statistics for a short code.

```http
GET /stats/aZ19xQ HTTP/1.1
```

Successful response (`200 OK`):

```json
{
  "shortCode": "aZ19xQ",
  "longUrl": "https://example.com/articles/spring",
  "clickCount": 42,
  "createdAt": "2026-11-09T10:15:30",
  "expiresAt": "2026-12-09T10:15:30"
}
```

Relevant errors: `404 Not Found` for an unknown code and `410 Gone` for an expired link. Error responses use the `status`, `error`, and `message` fields shown above.

### Interactive API documentation

- Swagger UI: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
- OpenAPI JSON: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

## Prerequisites

For running the application locally without containers:

- Java 21
- MySQL 8, with a database and application user
- Redis 7 listening on `localhost:6379`

For the containerized setup:

- Docker Engine/Desktop with the Compose plugin
- Ports `8080` and `3307` available

The Maven Wrapper (`mvnw` / `mvnw.cmd`) downloads and runs the Maven version configured by the project; a separate Maven installation is not required.

## Configuration

| Variable | Default / Compose value | Purpose |
| --- | --- | --- |
| `DB_URL` | Local default: `jdbc:mysql://localhost:3306/url_shortener`; Compose: `jdbc:mysql://mysql:3306/url_shortener` | JDBC connection URL |
| `DB_USERNAME` | Required | MySQL application user |
| `DB_PASSWORD` | Required | MySQL application password |
| `REDIS_HOST` | Local default: `localhost`; Compose: `redis` | Redis hostname |
| `REDIS_PORT` | `6379` | Redis port |
| `URL_EXPIRY` | `30d` | Link lifetime; accepts Spring duration syntax |
| `SHORTEN_RATE_LIMIT_MAX_REQUESTS` | `10` | Maximum shorten requests per IP per rate-limit window |
| `SHORTEN_RATE_LIMIT_WINDOW_SECONDS` | `60` | Rate-limit window length |

The Compose file requires `DB_USERNAME`, `DB_PASSWORD`, and `MYSQL_ROOT_PASSWORD` from a local `.env` file. Do not commit `.env` or put real credentials in documentation. Optional application settings such as `URL_EXPIRY` and the rate-limit values are supported by Spring configuration; to override them in Compose, pass them through the `app` service's `environment` section.

## Run with Docker Compose

From the project root, create your local environment file and replace the placeholders with strong, unique passwords:

```powershell
Copy-Item .env.example .env
notepad .env
```

Then build and start the services:

```powershell
docker compose up --build -d
docker compose ps
```

Compose starts the application after MySQL and Redis report healthy. The API is available at `http://localhost:8080`; MySQL is exposed to the host on port `3307`, and its data persists in the `mysql_data` volume. Redis is available to the application over the Compose network.

View logs or stop the services:

```powershell
docker compose logs -f app
docker compose down
```

`docker compose down` keeps the database volume. To also remove persisted database data, run `docker compose down -v` deliberately.

## Run locally

1. Start MySQL 8 and Redis 7. Create the database `url_shortener` and a dedicated MySQL application user with privileges on that database. Do not use the MySQL root account for the application.
2. Set the environment variables in the shell where the application will run. For PowerShell, for example:

   ```powershell
   $env:DB_URL = "jdbc:mysql://localhost:3306/url_shortener"
   $env:DB_USERNAME = "url_shortener_app"
   $env:DB_PASSWORD = "<your-local-database-password>"
   $env:REDIS_HOST = "localhost"
   $env:REDIS_PORT = "6379"
   ```

3. Start the application:

   ```powershell
   .\mvnw.cmd spring-boot:run
   ```

The schema is managed with Hibernate's `ddl-auto=update` setting. For a packaged local run, use `.\mvnw.cmd -DskipTests package`, then `java -jar target/urlshortener-0.0.1-SNAPSHOT.jar` with the same environment variables set.

## Tests

Run the full suite with:

```powershell
.\mvnw.cmd test
```

The existing suite uses H2 for database-oriented tests and includes a Redis connectivity test. Make sure Redis is listening at `localhost:6379` before running the full suite. Tests cover URL validation, Base62 encoding, persistence and schema behavior, caching, rate limiting, controller behavior, and OpenAPI document generation.

## Project structure

```text
src/
|-- main/
|   |-- java/com/farhan/urlshortener/
|   |   |-- cache/          Redis URL cache
|   |   |-- config/         OpenAPI metadata
|   |   |-- encoder/        Base62 ID encoder
|   |   |-- entity/         JPA URL entity
|   |   |-- repository/     JPA persistence and atomic click update
|   |   |-- service/        Shortening, rate limit, statistics, cleanup
|   |   |-- validation/     HTTP(S) URL constraint and validator
|   |   `-- web/            REST controller, errors, request/response DTOs
|   `-- resources/          Spring application configuration
`-- test/
    |-- java/               Unit, integration, API, and OpenAPI tests
    `-- resources/          Test database fixtures

Dockerfile                  Multi-stage Java 21 build and runtime image
compose.yaml                Application, MySQL, Redis, health checks, volume
.env.example                Placeholder local Compose configuration
.dockerignore               Docker build context exclusions
mvnw / mvnw.cmd             Maven Wrapper scripts
pom.xml                     Maven dependencies and build configuration
```

## Possible future improvements

- Add authentication and authorization for link creation and statistics.
- Add custom aliases and configurable per-link expiration through the API.
- Add an endpoint for link deletion and owner-specific management.
- Add richer analytics, such as time-series click data, while considering privacy.
- Add production observability, including metrics, tracing, and health endpoints.
- Use managed secrets and explicit database migrations for production deployments.
- Add end-to-end tests that run against the Compose services in CI.

These are ideas for future work; they are not current API features.

# Webchat Project Structure

Khung folder duoc tao dua tren tai lieu `webchat_system_design.docx`.

## Root

- `wechatfrontend/`: React + Vite client.
- `wechatbackend/`: Spring Boot backend.
- `gateway/`: gateway service placeholder cho huong mo rong distributed/microservice.
- `nginx/`: reverse proxy va load balancing config.
- `docker/`: Dockerfile/config rieng cho tung service.
- `postgres/`: init script va migration database.
- `redis/`: Redis config placeholder.
- `monitoring/`: Prometheus va Grafana.
- `storage/minio/`: MinIO/S3-compatible storage config placeholder.
- `docs/`: tai lieu kien truc, API, database, deployment, monitoring.

## Backend Modules

- `auth`: dang ky, dang nhap, refresh token, JWT.
- `user`: quan ly user, profile, online status.
- `conversation`: chat 1-1 va group conversation.
- `message`: message, delivered/seen, recall/delete.
- `realtime`: WebSocket/STOMP gateway va events.
- `notification`: notification va push event.
- `media`: upload anh, file, voice.
- `friendship`: friend/contact workflow.
- `search`: tim kiem user, group, message.
- `admin`: admin dashboard backend.
- `common`: exception, response, security, validation shared code.
- `config`: Spring configuration.
- `resources/db/migration`: database migration scripts.

## Frontend Modules

- `features/auth`: authentication screens, services, components.
- `features/chat`: chat UI, message composer, message thread.
- `features/conversations`: conversation list va conversation state.
- `features/contacts`: friend/contact UI.
- `features/notifications`: notification UI va services.
- `features/profile`: profile/settings.
- `features/media`: media/file upload.
- `features/search`: search message/user/group.
- `services/socket`: STOMP/SockJS realtime client.
- `services/api`: REST API client.
- `store`: Redux Toolkit/Zustand state.
- `routes`, `layouts`, `pages`, `hooks`, `types`, `utils`: shared frontend structure.

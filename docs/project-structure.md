# Webchat Project Structure

Khung folder duoc tao dua tren tai lieu `webchat_system_design.docx`.

## Root

- `wechatfrontend/`: React + Vite client.
- `wechatbackend/`: Spring Boot backend.
- `docker/`: Docker Compose va toan bo Dockerfile/config runtime cua `backend`, `frontend`, va `gateway`.
  - `docker-compose.yml`: stack `gateway`, `frontend`, `backend`, `postgres`, va `redis`.
  - `backend/Dockerfile`: Spring Boot image.
  - `frontend/Dockerfile`: React/Vite build va static Nginx image.
  - `docker/gateway/Dockerfile`, `docker/gateway/nginx.conf`: reverse proxy Nginx.
- `wechatbackend/src/main/resources/db/migration/`: Flyway migrations duoc load tu classpath.
- `wechatbackend/src/main/.../attachment/storage/`: storage abstraction, local test implementation va private Supabase S3-compatible implementation.
- `monitoring/`: Prometheus va Grafana configuration.
- `docs/`: tai lieu kien truc, API, database, deployment, monitoring.

PostgreSQL va Redis la Compose services duoc khai bao trong `docker/docker-compose.yml`; gateway va storage runtime duoc quan ly boi `docker/` va backend adapter, khong co source runtime rieng o top-level.

## Backend Modules

- `auth`: dang ky, dang nhap, refresh token, JWT.
- `user`: quan ly user, profile, online status.
- `conversation`: chat 1-1 va group conversation.
- `message`: message, delivered/seen, recall/delete.
- `attachment`: attachment upload/download va metadata.
- `media`: media gallery va file management.
- `realtime`: WebSocket/STOMP gateway va events.
- `notification`: notification va push event.
- `friendship`: friend/contact workflow.
- `search`: tim kiem user, group, message.
- `audit`: audit logging va request context.
- `cleanup`: cleanup jobs va retention policies.
- `role`: roles, permissions va user-role management.
- `admin`: admin dashboard backend.
- `common`: exception, response, security, validation shared code.
- `config`: Spring configuration.

## Frontend Modules

- `features/auth`: authentication screens, services, components.
- `features/chat`: chat UI, message composer, message thread.
- `features/conversations`: conversation list va conversation state.
- `features/contacts`: friend/contact UI.
- `features/messages`: message list, composer, attachments va message actions.
- `features/notifications`: notification UI va services.
- `features/realtime`: realtime connection state va event bridge.
- `features/profile`: profile/settings.
- `features/media`: media/file upload.
- `features/search`: search message/user/group.
- `services/websocket`: STOMP realtime client va reconnect policy.
- `services/api`: REST API client.
- `services/auth`: token storage va authentication helpers.
- `store`: Redux Toolkit/Zustand state.
- `routes`, `layouts`, `pages`, `hooks`, `types`, `utils`: shared frontend structure.

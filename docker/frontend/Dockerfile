FROM node:24.21.0-alpine3.24 AS build

WORKDIR /build
COPY wechatfrontend/package.json wechatfrontend/package-lock.json ./
RUN npm ci

COPY wechatfrontend/ ./
RUN npm run build

FROM nginx:1.31.5-alpine3.24 AS runtime

COPY docker/frontend/nginx.conf /etc/nginx/nginx.conf
COPY --from=build --chown=nginx:nginx /build/dist/ /usr/share/nginx/html/

RUN mkdir -p /var/cache/nginx /var/run/nginx /tmp/nginx \
	&& chown -R nginx:nginx /var/cache/nginx /var/run/nginx /tmp/nginx /usr/share/nginx/html

USER nginx
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
	CMD wget -q -T 3 -O /dev/null http://127.0.0.1:8080/health || exit 1

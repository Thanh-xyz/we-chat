FROM node:24.21.0-alpine3.24 AS frontend-build

WORKDIR /build
COPY wechatfrontend/package.json wechatfrontend/package-lock.json ./
RUN npm ci

COPY wechatfrontend/ ./
RUN npm run build

FROM nginx:1.31.5-alpine3.24 AS runtime

COPY nginx/nginx.conf /etc/nginx/nginx.conf
COPY --from=frontend-build /build/dist/ /usr/share/nginx/html/

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
	CMD wget -q -T 3 -O /dev/null http://127.0.0.1:8080/actuator/health || exit 1


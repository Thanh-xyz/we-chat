FROM nginx:1.31.5-alpine3.24 AS runtime

COPY docker/gateway/nginx.conf /etc/nginx/nginx.conf

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
	CMD wget -q -T 3 -O /dev/null http://127.0.0.1:8080/health || exit 1

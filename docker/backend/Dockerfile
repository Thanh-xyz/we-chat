FROM maven:3.9.16-eclipse-temurin-21-alpine AS build

WORKDIR /build
COPY wechatbackend/pom.xml ./pom.xml
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline

COPY wechatbackend/src ./src
RUN mvn --batch-mode --no-transfer-progress -DskipTests package

FROM eclipse-temurin:21-jre-alpine-3.24 AS runtime

RUN addgroup -S app \
	&& adduser -S -G app -u 10001 app \
	&& mkdir -p /app/uploads \
	&& chown -R app:app /app

WORKDIR /app
COPY --from=build --chown=app:app /build/target/wechat-0.0.1-SNAPSHOT.jar ./application.jar

USER 10001:10001
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
	CMD wget -q -T 3 -O /dev/null http://127.0.0.1:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "-jar", "/app/application.jar"]

# ==========================================
# Stage 1: Gradle Build (Java 21)
# ==========================================
FROM eclipse-temurin:21.0.13_9-jdk-alpine AS builder
WORKDIR /app

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle

# Windows CRLF 개행문자 호환 처리 및 실행 권한 부여
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

# 의존성 캐싱 (소스만 바뀔 때 빌드 속도 향상)
RUN ./gradlew dependencies --no-daemon || true

COPY src ./src
RUN ./gradlew bootJar -x test --no-daemon

# ==========================================
# Stage 2: Slim Runtime (JRE 21)
# ==========================================
FROM eclipse-temurin:21.0.13_9-jre-alpine
WORKDIR /app

# 한국 시간대(KST) 설정 및 헬스체크용 curl 설치
RUN apk add --no-cache tzdata curl && \
    cp /usr/share/zoneinfo/Asia/Seoul /etc/localtime && \
    echo "Asia/Seoul" > /etc/timezone && \
    mkdir -p /app/logs

ENV TZ=Asia/Seoul
ENV SERVER_PORT=8080

COPY --from=builder /app/build/libs/*-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-Xms256m", "-Xmx512m", "-Duser.timezone=Asia/Seoul", "-jar", "app.jar"]

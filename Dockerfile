# 多阶段构建：构建镜像和运行镜像分开，最终镜像里不含 Maven、源码、构建缓存。
# 真实工作中这一点很重要 —— 镜像越小，拉取越快，攻击面越小。
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

# 先只拷 pom 并预热依赖：只要 pom 没变，这一层就能命中缓存，
# 后续改业务代码时构建从几分钟降到几十秒。
COPY pom.xml .
COPY flowmart-common/pom.xml flowmart-common/
COPY flowmart-bootstrap/pom.xml flowmart-bootstrap/
RUN mvn -B -q dependency:go-offline -DskipTests || true

COPY . .
RUN mvn -B clean package -DskipTests

# ---- 运行阶段 ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN apk add --no-cache tzdata wget \
    && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone \
    && addgroup -S app && adduser -S app -G app

COPY --from=builder /build/flowmart-bootstrap/target/flowmart.jar app.jar
RUN mkdir -p /app/logs && chown -R app:app /app

# 不要用 root 跑应用 —— 容器逃逸时 root 权限危害大得多
USER app

ENV JAVA_OPTS="-Xms512m -Xmx512m -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/app/logs" \
    SPRING_PROFILES_ACTIVE=test

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=45s --retries=5 \
    CMD wget -qO- http://localhost:8080/actuator/health/readiness || exit 1

# exec 形式 + $JAVA_OPTS 展开，保证 java 是 PID 1，能正确收到 SIGTERM 触发优雅停机
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

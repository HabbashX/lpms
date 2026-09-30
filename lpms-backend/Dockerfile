# ---- build stage: Maven + JDK 25 -----------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS maven
FROM eclipse-temurin:25-jdk AS build

# reuse the Maven installation from the official image (Maven is JDK-agnostic)
COPY --from=maven /usr/share/maven /usr/share/maven
ENV M2_HOME=/usr/share/maven \
    PATH=/usr/share/maven/bin:$PATH

WORKDIR /build
COPY pom.xml .
RUN mvn -B -q -DskipTests dependency:go-offline || true

COPY src ./src
RUN mvn -B -q clean package -Dmaven.test.skip=true

# ---- runtime stage: JRE 25 -----------------------------------------------
FROM eclipse-temurin:25-jre

RUN groupadd --system app && useradd --system --gid app app

WORKDIR /app
COPY --from=build /build/target/lpms-backend-1.0.0.jar app.jar

USER app
EXPOSE 8080

ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]

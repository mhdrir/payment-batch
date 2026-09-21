# ---------- build stage ----------
# The application is built inside Docker, so no local Java/Maven installation is required.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Dependencies are resolved in their own layer so code changes do not re-download them.
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B clean package

# ---------- runtime stage ----------
FROM amazoncorretto:21.0.11
WORKDIR /app

# input and output are bind mounts at runtime, data holds the H2 database file
RUN mkdir -p /app/input /app/output /app/data

COPY --from=build /build/target/payment-batch.jar /app/payment-batch.jar

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/payment-batch.jar"]


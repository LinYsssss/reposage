FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
# The runner shells out to the Docker CLI to launch constrained analysis containers. It talks to a
# restricted Docker socket proxy (see docker-compose.yml), never the raw host socket, and the
# analyzed containers never receive Docker access themselves.
RUN apt-get update \
    && apt-get install -y --no-install-recommends docker-cli ca-certificates \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/target/sandbox-runner-0.1.0-SNAPSHOT.jar app.jar
# No EXPOSE: this service has no inbound HTTP port; it only consumes a RabbitMQ queue.
ENTRYPOINT ["java", "-jar", "app.jar"]

# syntax=docker/dockerfile:1
FROM hsldevcom/infodevops-docker-base-images:1.0.2-25-java-jdk AS build

WORKDIR /usr/app
COPY . .
COPY .mvn/settings.xml /root/.m2/settings.xml

RUN --mount=type=secret,id=github_token \
    --mount=type=secret,id=github_actor \
    GITHUB_TOKEN="$(cat /run/secrets/github_token)" && export GITHUB_TOKEN && \
    GITHUB_ACTOR="$(cat /run/secrets/github_actor)" && export GITHUB_ACTOR && \
    ./mvnw -B package -DskipTests

FROM hsldevcom/infodevops-docker-base-images:1.0.2-25-java-jre

WORKDIR /usr/app
COPY --from=build /usr/app/target/transitlog-alert-sink.jar /usr/app/transitlog-alert-sink.jar
COPY --from=build /usr/app/run /run
RUN useradd --system --uid 10001 --create-home --home-dir /home/app --shell /usr/sbin/nologin app
USER app

ENTRYPOINT ["java", "-XX:InitialRAMPercentage=10.0", "-XX:MaxRAMPercentage=95.0", "-jar", "/usr/app/transitlog-alert-sink.jar"]


FROM openjdk:8-jre-slim

#Install curl for health check
RUN apt-get update && apt-get install -y --no-install-recommends curl

#This container can access the build artifacts inside the BUILD container.
#Everything that is not copied is discarded
ADD target/transitlog-alert-sink.jar /usr/app/transitlog-alert-sink.jar
COPY run /run
COPY start-application.sh /
RUN chmod +x /start-application.sh
CMD ["/start-application.sh"]

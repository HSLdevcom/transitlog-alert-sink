FROM eclipse-temurin:11-alpine
#Install curl for health check
RUN apk add --no-cache curl

#This container can access the build artifacts inside the BUILD container.
#Everything that is not copied is discarded
ADD target/transitlog-alert-sink.jar /usr/app/transitlog-alert-sink.jar
COPY run /run
COPY start-application.sh /
RUN chmod +x /start-application.sh
CMD ["/start-application.sh"]

#!/bin/sh

java -XX:InitialRAMPercentage=10.0 -XX:MaxRAMPercentage=95.0 -Ddb.username=$(cat /run/secrets/TRANSITLOG_TIMESCALE_USERNAME) -Ddb.password=$(cat /run/secrets/TRANSITLOG_TIMESCALE_PASSWORD) -jar /usr/app/transitlog-alert-sink.jar
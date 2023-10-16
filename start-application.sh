#!/bin/sh

java -XX:InitialRAMPercentage=10.0 -XX:MaxRAMPercentage=95.0 -Ddb.username=$TRANSITLOG_TIMESCALE_USERNAME -Ddb.password=$TRANSITLOG_TIMESCALE_PASSWORD -jar /usr/app/transitlog-alert-sink.jar
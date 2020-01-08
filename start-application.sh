#!/bin/bash

java -Xms256m -Xmx4096m -Ddb.username=$(cat /run/secrets/TRANSITLOG_TIMESCALE_USERNAME) -Ddb.password=$(cat /run/secrets/TRANSITLOG_TIMESCALE_PASSWORD) -jar /usr/app/transitlog-alert-sink.jar
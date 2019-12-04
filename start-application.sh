#!/bin/bash

java -Xms256m -Xmx4096m -Ddb_username=$(cat /run/secrets/TRANSITLOG_TIMESCALE_USERNAME) -Ddb_password=$(cat /run/secrets/TRANSITLOG_TIMESCALE_PASSWORD) -jar /usr/app/transitlog-alert-sink.jar
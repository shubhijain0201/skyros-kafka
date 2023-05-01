#!/bin/bash

mvn exec:java -Dexec.mainClass=io.skyrosforkafka.KafkaClient -Dexec.args="--c config.properties --o get --t storage598  --tm 1000"

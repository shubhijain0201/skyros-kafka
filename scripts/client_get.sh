#!/bin/bash

mvn exec:java -Dexec.mainClass=io.skyrosforkafka.KafkaClient -Dexec.args="--c config.properties --o get --t newtopic --tm 1000 --offset 17"

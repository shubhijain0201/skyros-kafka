#!/bin/bash

mvn exec:java -Dexec.mainClass=io.skyrosforkafka.KafkaClient -Dexec.args="--c config.properties --o get --t ramya --n 100000 --tm 1000 --offset 600000"

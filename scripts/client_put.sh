#!/bin/bash

mvn exec:java -Dexec.mainClass=io.skyrosforkafka.KafkaClient -Dexec.args="--c config.properties --o put --op w_1 --c_id 1 --key_sep=, --parse_key=true --t hello"



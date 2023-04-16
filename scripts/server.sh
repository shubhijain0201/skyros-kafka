#!/bin/bash
S_ID=$1

mvn exec:java -Dexec.mainClass=io.skyrosforkafka.DurabilityServer -Dexec.args="--c config.properties --s_id $S_ID --k consumer_config.properties"
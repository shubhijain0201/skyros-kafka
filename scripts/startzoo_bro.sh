#!/bin/bash



declare -a nodes=("$USER@c220g5-110406.wisc.cloudlab.us" "$USER@c220g5-110407.wisc.cloudlab.us" "$USER@c220g5-110423.wisc.cloudlab.us" "$USER@c220g5-110404.wisc.cloudlab.us" "$USER@c220g5-110418.wisc.cloudlab.us")

for i in "${nodes[@]}"
do
    ssh -i ~/.ssh/id_ed25519 -t $i 'rm /tmp/zookeeper.log && rm /tmp/kafka.log'
    ssh -i ~/.ssh/id_ed25519 -t $i 'kill -9 $(lsof -t -i:9092) && sleep 5'
    ssh -i ~/.ssh/id_ed25519 -t $i 'kill -9 $(lsof -t -i:2181) && sleep 5'
    ssh -i ~/.ssh/id_ed25519 -t $i 'rm -rf /tmp/kafka-logs && rm -rf /tmp/zookeeper/version* && sleep 5'
    ssh -i ~/.ssh/id_ed25519 $i "/tmp/kafka/kafka/bin/zookeeper-server-start.sh /tmp/kafka/kafka/config/zookeeper.properties > /tmp/zookeeper.log 2>&1 && sleep 5" &
    ssh -i ~/.ssh/id_ed25519 $i "/tmp/kafka/kafka/bin/kafka-server-start.sh /tmp/kafka/kafka/config/server.properties > /tmp/kafka.log 2>&1 && sleep 5" &
done

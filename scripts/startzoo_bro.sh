#!/bin/bash

if [ $# -lt 1 ]; then
  echo "Error: Expects Cloudlab Username (Case-sensitive)"
  exit 1
fi


declare -a nodes=("$USER@c220g5-110406.wisc.cloudlab.us" "$USER@c220g5-110407.wisc.cloudlab.us" "$USER@c220g5-110423.wisc.cloudlab.us" "$USER@c220g5-110404.wisc.cloudlab.us" "$USER@c220g5-110418.wisc.cloudlab.us")

for i in "${nodes[@]}"
do
    ssh -i ~/.ssh/id_ed25519 -t $i 'kill -9 $(lsof -t -i:50051) && sleep 5'
    ssh -i ~/.ssh/id_ed25519 -t $i 'rm /kafka/zookeeper.log && rm /kafka/kafka.log'
    ssh -i ~/.ssh/id_ed25519 -t $i 'kill -9 $(lsof -t -i:9092) && sleep 5'
    ssh -i ~/.ssh/id_ed25519 -t $i 'kill -9 $(lsof -t -i:2181) && sleep 5'
    ssh -i ~/.ssh/id_ed25519 -t $i 'rm -rf /kafka/kafka-logs && rm -rf /kafka/zookeeper/version* && sleep 5'
    ssh -i ~/.ssh/id_ed25519 $i "/kafka/kafka/bin/zookeeper-server-start.sh /kafka/kafka/config/zookeeper.properties > /kafka/zookeeper.log 2>&1 && sleep 5 && lsof -i" &
    ssh -i ~/.ssh/id_ed25519 $i "/kafka/kafka/bin/kafka-server-start.sh /kafka/kafka/config/server.properties > /kafka/kafka.log 2>&1 && sleep 5" &
done

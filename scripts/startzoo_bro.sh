#!/bin/bash



declare -a nodes=("$USER@c220g2-011107.wisc.cloudlab.us" "$USER@c220g2-011110.wisc.cloudlab.us" "$USER@c220g2-011108.wisc.cloudlab.us" "$USER@c220g2-011112.wisc.cloudlab.us" "$USER@c220g2-011111.wisc.cloudlab.us")

for i in "${nodes[@]}"
do
    ssh -i ~/.ssh/id_ed25519 -t $i 'kill -9 $(lsof -t -i:9092)'
    ssh -i ~/.ssh/id_ed25519 -t $i 'kill -9 $(lsof -t -i:2181)'
    ssh -i ~/.ssh/id_ed25519 -t $i 'rm -rf /tmp/kafka-logs && rm -rf /tmp/zookeeper/version*'
    ssh -i ~/.ssh/id_ed25519 $i "/tmp/kafka/kafka/bin/zookeeper-server-start.sh /tmp/kafka/kafka/config/zookeeper.properties > /tmp/zookeeper.log 2>&1 & \
                /tmp/kafka/kafka/bin/kafka-server-start.sh /tmp/kafka/kafka/config/server.properties > /tmp/kafka.log 2>&1 &" &
done

#!/bin/bash

if [ $# -lt 1 ]; then
  echo "Error: Expects Cloudlab Username (Case-sensitive)"
  exit 1
fi

USER=$1

declare -a nodes=("$USER@c220g2-011107.wisc.cloudlab.us" "$USER@c220g2-011110.wisc.cloudlab.us" "$USER@c220g2-011108.wisc.cloudlab.us" "$USER@c220g2-011112.wisc.cloudlab.us" "$USER@c220g2-011111.wisc.cloudlab.us")

for i in "${nodes[@]}"
do
  #  ssh -i ~/.ssh/id_ed25519 -t $i 'mv /tmp/kafka/kafka/zookeeper.properties /tmp/kafka/kafka/config/zookeeper.properties'
  #  ssh -i ~/.ssh/id_ed25519 -t $i 'mv /tmp/kafka/kafka/server.properties /tmp/kafka/kafka/config/server.properties'
  # to set partitions
  # ssh -i ~/.ssh/id_ed25519 -t $i 'sed -i 's/^num\.partitions=.*/num.partitions=1/' /tmp/kafka/kafka/config/server.properties'
    
   ssh -o ServerAliveInterval=60 -i ~/.ssh/id_ed25519 -t $i 'cd /tmp/kafka/kafka & nohup /tmp/kafka/kafka/bin/zookeeper-server-start.sh /tmp/kafka/kafka/config/zookeeper.properties && nohup /tmp/kafka/kafka/bin/kafka-server-start.sh /tmp/kafka/kafka/config/server.properties &'
done


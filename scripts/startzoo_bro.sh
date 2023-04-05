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
   ssh -i ~/.ssh/id_ed25519 -t $i 'cd /tmp/kafka/kafka && /tmp/kafka/kafka/bin/zookeeper-server-start.sh /tmp/kafka/kafka/config/zookeeper.properties & /tmp/kafka/kafka/bin/kafka-server-start.sh /tmp/kafka/kafka/config/server.properties &'
done

/tmp/kafka/kafka/bin/kafka-topics.sh --create --topic my-topic --partitions 3 --replication-factor 3 --bootstrap-server localhost:9092
/tmp/kafka/kafka/bin/kafka-topics.sh --describe --topic my-topic --bootstrap-server localhost:9092
#!/bin/bash

if [ $# -lt 1 ]; then
  echo "Error: Expects Cloudlab Username (Case-sensitive)"
  exit 1
fi

USER=$1

declare -a nodes=("$USER@c220g5-110406.wisc.cloudlab.us" "$USER@c220g5-110407.wisc.cloudlab.us" "$USER@c220g5-110423.wisc.cloudlab.us" "$USER@c220g5-110404.wisc.cloudlab.us" "$USER@c220g5-110418.wisc.cloudlab.us")

for i in "${nodes[@]}"
do
    ssh -i ~/.ssh/id_ed25519 -t $i 'cd /tmp && sudo mkdir kafka && sudo chmod 777 /tmp/kafka/'
    ssh -i ~/.ssh/id_ed25519 -t $i 'cd /tmp/kafka && wget https://dlcdn.apache.org/kafka/3.4.0/kafka_2.13-3.4.0.tgz && tar -xzf kafka_2.13-3.4.0.tgz && rm kafka_2.13-3.4.0.tgz*'
    ssh -i ~/.ssh/id_ed25519 -t $i 'rm -rf /tmp/kafka-logs'
done
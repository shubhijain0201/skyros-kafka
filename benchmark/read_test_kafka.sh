#!/bin/bash

# install gnu-parallel
# sudo apt-get update
# sudo apt-get install parallel

user=$1
num_clients=$2
topic=$3
# zipfian or uniform
type=$4

kafka_offsets=$(/kafka/kafka/bin/kafka-run-class.sh kafka.tools.GetOffsetShell --broker-list localhost:9092 --topic $topic)

# Extract low and high offsets from output
low=$(echo "$kafka_offsets" | awk -F ":" '{print $2}')
high=$(echo "$kafka_offsets" | awk -F ":" '{print $3}')

echo "Low offset: $low"
echo "High offset: $high"

python generate_read_offsets.py $num_clients $type $low $high

export SHELL=$(type -p bash)
export topic=$topic
run_executable() {
    OFFSET=$1
    echo "shell fetching from offset ${OFFSET}"
    /kafka/kafka/bin/kafka-consumer-perf-test.sh --topic ${topic} --bootstrap-server localhost:9092 --group test-group --messages 100000 --offset ${OFFSET}

}

export -f run_executable

echo $(pwd)
offsets=()
while IFS= read -r line; do
  offsets+=("$line")
done < "${type}_offsets.txt"
echo "${offsets[@]}"

# exit
#  Run gnu-parallel

parallel run_executable {} ::: "${offsets[@]}"

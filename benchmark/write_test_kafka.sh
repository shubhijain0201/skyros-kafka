#!/bin/bash

# install gnu-parallel
#sudo apt-get update
#sudo apt-get install parallel

clients=5

export SHELL=$(type -p bash)

# create topic and set replication factor
/kafka/bin/kafka-topics.sh  --create --topic test-4 --replication-factor 3 --bootstrap-server localhost:9092

run_executable() {
    echo "started executable"
    original_string=$1
    number=${original_string#./payloads_kv}
    CLIENT_ID=${number%.txt}
    input_file=/skyros-kafka/benchmark/workload/$1

    echo "Processing ${input_file} and id ${CLIENT_ID}"

    /kafka/bin/kafka-producer-perf-test.sh --topic test-4 --payload-file ${input_file} --producer-props bootstrap.servers=localhost:9092 --throughput -1 --num-records 1000
}

export -f run_executable

echo $(pwd)
pushd ./benchmark/workload
input_files=($(ls -1t ./payloads_kv*.txt | head -n $clients))
client_ids=($(seq 1 $clients))
echo "${input_files[@]}"
popd

# exit
#  Run gnu-parallel

parallel run_executable {} ::: "${input_files[@]}"

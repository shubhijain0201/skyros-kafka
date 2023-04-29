#!/bin/bash

# install gnu-parallel
#sudo apt-get update
#sudo apt-get install parallel

user=$1
clients=$2
topic=$3
acks=$4
export SHELL=$(type -p bash)

# create topic and set replication factor
/kafka/kafka/bin/kafka-topics.sh  --create --topic $topic --replication-factor 3 --bootstrap-server localhost:9092 

export user=$user
export clients=$clients
export topic=$topic
export acks=$acks
run_executable() {
    #echo "started executable"
    original_string=$1
    number=${original_string#./payloads_kv}
    CLIENT_ID=${number%.txt}
    input_file=/users/$user/skyros-kafka/benchmark/workload/$1

    #echo "Processing ${input_file} and id ${CLIENT_ID}"

    #/kafka/kafka/bin/kafka-producer-perf-test.sh --topic ${topic} --payload-file ${input_file} --producer-props bootstrap.servers=localhost:9092 acks=${acks} linger.ms=0 enable.idempotence=false --throughput -1 --num-records 100000  
   /kafka/kafka/bin/kafka-producer-perf-test.sh --topic ${topic} --producer-props bootstrap.servers=localhost:9092 acks=${acks} linger.ms=0 enable.idempotence=false max.in.flight.requests.per.connection=1000 --throughput -1 --num-records 100000 --record-size 20
}

export -f run_executable

#echo $(pwd)
pushd ./benchmark/workload
input_files=($(ls -1t ./payloads_kv*.txt | head -n $clients))
client_ids=($(seq 1 $clients))
#echo "${input_files[@]}"
popd

# exit
#  Run gnu-parallel

parallel run_executable {} ::: "${input_files[@]}"

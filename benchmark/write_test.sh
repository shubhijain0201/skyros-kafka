#!/bin/bash

# install gnu-parallel
#sudo apt-get update
#sudo apt-get install parallel

USER=$1
clients=5

export SHELL=$(type -p bash)
export USER=${USER}

run_executable() {
    echo "started executable"
    input_file=/users/$USER/skyros-kafka/benchmark/workload/$1
    CLIENT_ID=$2
    echo "Processing ${input_file} and id ${CLIENT_ID}"

    mvn exec:java -Dexec.mainClass=io.skyrosforkafka.KafkaClient -Dexec.args="--c config.properties --o put --op w_all --c_id ${CLIENT_ID} --key_sep=, --parse_key=true --t hello --i ${input_file}"
    
}

export -f run_executable

echo $(pwd)
pushd ./benchmark/workload
input_files=($(ls -1t ./payloads_kv*.txt | head -n $clients))
client_ids=($(seq 1 $clients))
echo "${input_files[@]}"
popd

echo $input_files
# exit
#  Run gnu-parallel

parallel run_executable {} ::: "${input_files[@]}" ::: "${client_ids[@]}"

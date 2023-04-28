#!/bin/bash

# install gnu-parallel
#sudo apt-get update
#sudo apt-get install parallel

if [ $# -ne 2 ]; then
  echo "Error: Expects Cloudlab Username (Case-sensitive) Topic name"
  exit 1
fi


USER=$1
TOPIC=$2
echo $2
clients=8

export SHELL=$(type -p bash)
export USER=${USER}
export TOPIC=${TOPIC}

run_executable() {
    echo "started executable"
    original_string=$1
    number=${original_string#./payloads_kv}
    CLIENT_ID=${number%.txt}
    input_file=/users/$USER/skyros-kafka/benchmark/workload/$1

    echo "Processing ${input_file} and id ${CLIENT_ID}"

    mvn --version

   mvn exec:java -Dexec.mainClass=io.skyrosforkafka.KafkaClient -Dexec.args="--c config.properties --o put --op w_all --c_id ${CLIENT_ID} --key_sep=, --parse_key=true --t ${TOPIC} --i ${input_file}"
    
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

parallel run_executable {} ::: "${input_files[@]}"

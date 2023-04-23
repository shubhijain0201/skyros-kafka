#!/bin/bash

# install gnu-parallel
sudo apt-get update
sudo apt-get install parallel

CLIENT_ID=$1

clients=5

command="mvn exec:java -Dexec.mainClass=io.skyrosforkafka.KafkaClient -Dexec.args='--c config.properties --o put --op w_all --c_id ${CLIENT_ID} --key_sep=, --parse_key=true --t hello-topic --i '"

export SHELL=$(type -p bash)
export command=$command

run_executable() {
    input_file=$1
    echo "Processing ${input_file}"
    $command${input_file} 
}

export -f run_executable

echo $(pwd)
pushd workload
input_files=($(ls -1t ./payloads_kv*.txt | head -n $clients))
echo "${input_files[@]}"
popd

echo $input_files
# exit
#  Run gnu-parallel

parallel run_executable {} ::: "${input_files[@]}"

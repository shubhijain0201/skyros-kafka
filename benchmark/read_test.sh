#!/bin/bash

# install gnu-parallel
# sudo apt-get update
# sudo apt-get install parallel

USER=$1
num_clients=$2

# zipfian or uniform
type=$3

python3 generate_read_offsets.py $num_clients $type

export SHELL=$(type -p bash)
export USER=${USER}

run_executable() {
    OFFSET=$1
    echo "Fetching from offset ${OFFSET}"
    mvn exec:java -Dexec.mainClass=io.skyrosforkafka.KafkaClient -Dexec.args="--c config.properties --o get --t newtopic --tm 1000 --offset ${OFFSET}"
}

export -f run_executable

echo $(pwd)
pushd ./benchmark
offsets=()
while IFS= read -r line; do
  offsets+=("$line")
done < "${type}_offsets.txt"
echo "${offsets[@]}"
popd

# exit
#  Run gnu-parallel

parallel run_executable {} ::: "${offsets[@]}"

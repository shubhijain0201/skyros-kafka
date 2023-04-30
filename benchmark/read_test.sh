#!/bin/bash

# install gnu-parallel
# sudo apt-get update
# sudo apt-get install parallel

USER=$1
num_clients=$2

# zipfian or uniform
type=$3
topic=$4
python3 /users/$USER/skyros-kafka/benchmark/generate_read_offsets.py $num_clients $type 1 100

export SHELL=$(type -p bash)
export USER=${USER}
export topic=${topic}
run_executable() {
    OFFSET=$1
    echo "Fetching from offset ${OFFSET}"
    echo "mvn exec:java -Dexec.mainClass=io.skyrosforkafka.KafkaClient -Dexec.args=\"--c config.properties --o get --t ${topic}  --n 100000 --tm 1000 --offset ${OFFSET}\""
    mvn exec:java -Dexec.mainClass=io.skyrosforkafka.KafkaClient -Dexec.args="--c config.properties --o get --t ${topic}  --n 100000 --tm 1000 --offset ${OFFSET}"
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


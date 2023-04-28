#!/bin/bash

user=ak5hatha
topic=test-topic

export SHELL=$(type -p bash)

export user=$user
export topic=$topic

run_executable() {
    echo "started executable"
    original_string=$1
    number=${original_string#./payloads_kv}
    CLIENT_ID=${number%.txt}
    input_file=/users/$user/skyros-kafka/benchmark/workload/$1
    acks=$2
    clients=$3
    csv_file="./${clients}_${CLIENT_ID}_clients_acks_${acks}.csv"
    echo "Processing ${input_file} and id ${CLIENT_ID}"

    /kafka/kafka/bin/kafka-producer-perf-test.sh --topic ${topic} --payload-file ${input_file} --producer-props bootstrap.servers=localhost:9092 acks=${acks} linger.ms=0 enable.idempotence=false --throughput -1 --num-records 100000 | tail -n 1 | awk -v ack="${acks}" -v clients="${clients}" -v client_id="${CLIENT_ID}" '{print clients","ack","client_id","$1","$4","$8","$12","$16","$19","$22","$25}' > "$csv_file"  
}

export -f run_executable

for clients in 1 2 4 8 16; do
  for acks in 0 1 -1; do
   pushd ./benchmark/workload
   input_files=($(ls -1t ./payloads_kv*.txt | head -n $clients))
   client_ids=($(seq 1 $clients))
   popd
   parallel run_executable {} ::: "${input_files[@]}" ::: "${acks}" ::: "${clients}"
  done
done

output_file="output.csv"
export output_file=$output_file
echo "clients,acks,client_id,records sent,records/sec,avg latency,max latency,50th,95th,99th,99.9th" > "$output_file"

# merge csv files into one
# paste *_clients_acks_* > "$output_file"
for file in *_clients_acks_*.csv; do
    cat "$file" >> "$output_file"
done

# clean up csv files
rm *_clients_acks_*.csv

echo "Tests complete. Results written to $output_file"

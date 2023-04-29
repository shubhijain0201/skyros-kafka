#!/bin/bash
  
user=$1
topic=$2

export SHELL=$(type -p bash)

export user=$user
export topic=$topic

run_executable() {
    echo "started executable"
    original_string=$1
    number=${original_string#./payloads_kv}
    CLIENT_ID=${number%.txt}
    input_file=/users/$user/skyros-kafka/benchmark/workload/$1
    clients=$2
    csv_file="./${clients}_${CLIENT_ID}_clients.csv"
    echo "Processing ${input_file} and id ${CLIENT_ID}"

    mvn exec:java -Dexec.mainClass=io.skyrosforkafka.KafkaClient -Dexec.args="--c config.properties --o put --op w_all --c_id ${CLIENT_ID} --key_sep=, --parse_key=true --t ${topic} --i ${input_file}" | tail -n 1 | awk -v clients="${clients}" -v client_id="${CLIENT_ID}" '{print clients","client_id","$1","$4","$8","$12","$16","$19","$22","$25}' > "$csv_file"
}

export -f run_executable

for clients in 1 2 4 8 16; do
   pushd ./benchmark/workload
   input_files=($(ls -1t ./payloads_kv*.txt | head -n $clients))
   client_ids=($(seq 1 $clients))
   popd
   parallel run_executable {} ::: "${input_files[@]}" ::: "${clients}"
done

output_file="output_sk_write.csv"
export output_file=$output_file
echo "clients,client_id,records sent,records/sec,avg latency,max latency,50th,95th,99th,99.9th" > "$output_file"

# merge csv files into one
# paste *_clients_acks_* > "$output_file"
for file in *_clients.csv; do
    cat "$file" >> "$output_file"
done

# clean up csv files
rm *_clients.csv

echo "Tests complete. Results written to $output_file"

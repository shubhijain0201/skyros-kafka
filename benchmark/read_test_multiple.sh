user=$1
topic=$2

export SHELL=$(type -p bash)

export user=$user
export topic=$topic

run_executable() {
    OFFSET=$1
    clients=$2
    type=$3
    echo "shell fetching from offset ${OFFSET}"
    csv_file="./${clients}_${OFFSET}_clients_type_${type}.csv"
    mvn exec:java -Dexec.mainClass=io.skyrosforkafka.KafkaClient -Dexec.args="--c config.properties --o get --t ${topic} --tm 1000 --offset ${OFFSET}"| tail -n 1 | awk -v clients="${clients}" -v type="${type}" -v offset="${OFFSET}" '{print clients","type","offset","$0}' >> "$csv_file"
}

export -f run_executable

for clients in 1 2 4 8 16; do
  for type in uniform zipfian; do
    python3 /users/$user/skyros-kafka/benchmark/generate_read_offsets.py $clients $type 1 100
    echo $(pwd)
    pushd benchmark/
    offsets=()
    while IFS= read -r line; do
      offsets+=("$line")
    done < "${type}_offsets.txt"
    echo "${offsets[@]}"
    popd
    parallel run_executable {} ::: "${offsets[@]}" ::: "${clients}" ::: "${type}"
  done
done

output_file="output_sk_read.csv"
export output_file=$output_file
echo "clients, type, offset, start.time, end.time, data.consumed.in.MB, MB.sec, data.consumed.in.nMsg, nMsg.sec, rebalance.time.ms, fetch.time.ms, fetch.MB.sec, fetch.nMsg.sec" > "$output_file"

for file in *_clients_type_*.csv; do
    cat "$file" >> "$output_file"
done

rm *_clients_type_*.csv

echo "Tests complete. Results written to $output_file"
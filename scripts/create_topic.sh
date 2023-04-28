
if [ $# -ne 2 ]; then
  echo "Usage: $0 [Topic] [Replication factor]"
  exit 1
fi
/kafka/kafka/bin/kafka-topics.sh --create --topic $1 --partitions 1 --replication-factor $2 --bootstrap-server localhost:9092

/kafka/kafka/bin/kafka-topics.sh --describe --topic $1 --bootstrap-server localhost:9092
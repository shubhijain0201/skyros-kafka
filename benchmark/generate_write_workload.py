import random
import string

num_records = 100000
key_size = 10
value_size = 10

for j in range(16):
    with open('/users/ak5hatha/skyros-kafka/benchmark/workload/payloads_kv'+str(j)+'.txt', 'w') as file:
        for i in range(num_records):
            key = ''.join(random.choices(string.ascii_lowercase, k=key_size))
            value = ''.join(random.choices(string.ascii_lowercase, k=value_size))
            file.write(f"{key},{value}\n")

import random
import string

def get_random_string(length):
    letters = string.ascii_lowercase
    result_str = ''.join(random.choice(letters) for i in range(length))
    return result_str

all_keys = []
key_value_dict = {}

with open('../inputs/input.txt', 'r') as f:
    for line in f:
        parts = line.strip().split()
        if len(parts) == 3 and parts[0] == "put":
            key = parts[1]
            all_keys.append(key)
            key_value_dict[key] = parts[2]

clients = 32
for i in range(clients):      
    total_keys = 100000
    keys_present = int(0.25 * total_keys)

    read_selected_keys = random.sample(all_keys, keys_present)
    write_selected_keys = random.sample(all_keys, keys_present)

    workload = {}

    for key in read_selected_keys:
        workload[key] = "get " + key

    for key in write_selected_keys:
        workload[key] = "put " + key + " " + get_random_string(10)

    j = len(workload)
    while j < total_keys:
        new_key = get_random_string(24)
        if new_key not in all_keys and new_key not in workload:
            workload[new_key] = "put " + new_key + " " + get_random_string(10)
            j += 1

    with open(f'../inputs/read_write_workload_input_{i}.txt', 'w') as f:
        print("Writing to file:\n")
        for line in workload.values():
            f.write(line + "\n")

    print(f"Random read keys selected length: {len(read_selected_keys)}")
    print(f"Random write keys selected length: {len(write_selected_keys)}")
    print("Generating read and write workload:\n")
    print(f"Workload length: {len(workload)}")

    lines = open(f'../inputs/read_write_workload_input_{i}.txt').readlines()
    random.shuffle(lines)
    open(f'../inputs/read_write_workload_input_{i}.txt', 'w').writelines(lines)
    print(f"{i}.done")

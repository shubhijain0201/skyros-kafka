import random
import string

clients = 32

def get_random_string(length):
    letters = string.ascii_lowercase
    result_str = ''.join(random.choice(letters) for i in range(length))
    return result_str

total_keys = 100000
keys_present = int(0.5 * total_keys)

#print(len(all_keys))

for i in range(clients):
    all_keys = set(line.strip().split()[1] for line in open('../inputs/write_workload/write_workload_input_' + str(i) + ".txt")
               if line.strip().startswith("put "))
    selected_keys = set(random.sample(all_keys, keys_present))
    while len(selected_keys) < total_keys:
        new_key = get_random_string(24)
        if new_key not in all_keys and new_key not in selected_keys:
            selected_keys.add(new_key)

    selected_keys = list(selected_keys)
    random.shuffle(selected_keys)

    with open(f'../inputs/read_workload/read_workload_input_' + str(i) + '.txt', 'w') as f:
        f.writelines(f"get {key}\n" for key in selected_keys)

    print(f'read_workload_input_{i}.txt - Done')

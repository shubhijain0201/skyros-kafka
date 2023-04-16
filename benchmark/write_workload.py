import random
import string
import sys

def get_random_string(length):
    letters = string.ascii_lowercase
    result_str = ''.join(random.choice(letters) for i in range(length))
    return result_str

clients = 32
total_keys = 1000 * 100
for i in range(clients):
    with open('../inputs/write_workload/write_workload_input_' + str(i) + '.txt', 'w') as f:
        print("Writing to file: \n")
        for ii in range(total_keys):
            f.write("put " + get_random_string(24) + " " + get_random_string(10) + "\n")

    #lines = open('../inputs/write_workload_input_' + str(i) + '.txt').readlines()
    #random.shuffle(lines)
    #open('../inputs/write_workload_input_' + str(i) + '.txt', 'w').writelines(lines)
    print(str(i) + '.done')


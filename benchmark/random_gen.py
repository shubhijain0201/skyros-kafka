# This scripts generates a random read/write workload for read/writes.
# Usage: python random_gen.py arg
# The arg can be R, W or, WR.

import random
import string
import sys

def get_random_string(length):
    letters = string.ascii_lowercase
    result_str = ''.join(random.choice(letters) for i in range(length))
    return result_str

input_arg_len = len(sys.argv)
if (input_arg_len == 1):
    print("Incorrect input format")
    exit()

workload = 'W'
input_arg = sys.argv
if input_arg[1] == 'R':
    workload = 'R'
elif input_arg[1] == 'WR':
    workload = 'WR'

f = open("inputs/input.txt", "w")
for i in range(1000):
    # print(i)
    if workload == 'W':
        f.write("put " + get_random_string(24) + " " + get_random_string(10) + "\n")
    elif workload == 'R':
        f.write("get " + get_random_string(24) + "\n")

f.close()

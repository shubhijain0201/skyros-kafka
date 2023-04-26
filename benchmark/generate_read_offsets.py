import random
import sys

if len(sys.argv)>1:
    num_clients = int(sys.argv[1])
    zipfian = 1 if sys.argv[2]=='zipfian' else 0
    low = int(sys.argv[3])
    high = int(sys.argv[4])-100000
else:
    num_clients = 16
    zipfian = 0
    low = 1
    high = 100

if zipfian:
    # zipfian distribution: define the number of items and parameter in the distribution
    alpha = 3
    zipf_dist = [1.0 / pow(i, alpha) for i in range(low, high)]

    # Add some random noise to the weights
    for i in range(high):
        zipf_dist[i] *= random.uniform(0.5, 1.5)

    # Normalize the weights so they sum up to 1
    sum_zipf = sum(zipf_dist)
    zipf_dist = [x/sum_zipf for x in zipf_dist]

    # Generate a sample from the distribution
    zipfian_sample = random.choices(range(low, high), weights=zipf_dist, k=num_clients)

    with open('zipfian_offsets.txt', 'w') as f:
        for offset in zipfian_sample:
            f.write(str(offset) + '\n')

else:
    # uniform distribution
    uniform_sample = random.sample(range(low, high), num_clients)
    with open('uniform_offsets.txt', 'w') as f:
        for offset in uniform_sample:
            f.write(str(offset) + '\n')

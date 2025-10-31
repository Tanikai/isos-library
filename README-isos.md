# ISOS

## About

This repository contains an implementation of ISOS, based on the BFT-SMaRt
networking stack.

## Development

### Tooling

- Formatting: `google-java-format`

### Running locally

First, compile the project and copy it into four separate directories with the
`copy-library.sh` script. After that run four replicas with the
`quad-replica.sh` script together with the Java class name (`tmux` required):

```shell
./copy-library.sh

./quad-replica.sh smartrun.sh isos.benchmark.kvstore.KVStoreReplica
```

To exit, press Ctrl+C multiple times, or `tmux action key` + `&`, then confirm
with `y`.

The client can then be run with the `client.sh` script together with the Java
class name and the client ID:

```shell
./client.sh isos.benchmark.kvstore.KVStoreClientInteractive 0
```

## Configuration

TODO: configuration values for ISOS

```config
# The length of 
system.isos.replica.agreementSlotSequenceLength = 5000

# Missing here :

#
system.isos.replica.initialIsosTimeoutDeltaMillis = 3000

# The size of the expansion window of the 
system.isos.replica.executionWindowSize = 100
```

## Profiling

TODO Screenshot IntelliJ configuration

Run Replica 0 with the `KVStoreReplica` class via the IDE profiler. The
remaining replicas can be started with the `profiler-triple-replica.sh` script:

```shell
./profiler-triple-replica.sh smartrun.sh isos.benchmark.kvstore.KVStoreReplica
```

## Benchmarking

For ISOS, several changes were made to the networking stack of BFT-SMaRt. This
means that BFT-SMaRt consensus might not work as intended. Thus, the BFT-SMaRt
benchmarks used to compare with the performance of ISOS have to be copied into
a separate copy of the BFT-SMaRt repository. Follow these commands:

```shell
git clone https://github.com/bft-smart/library.git bft-smart-library
git checkout v2.0

```

### Benchmarking deployment to replica machines

The required dependencies and files for running ISOS can be automatically
installed to replicas with [Ansible](https://docs.ansible.com/). The control
node (the pc that manages the deployment) accesses the managed nodes (the
replicas) via SSH.

[Here is the documentation to install Ansible on your machine.](https://docs.ansible.com/ansible/latest/installation_guide/intro_installation.html).
It has to be a UNIX-like OS, which means that WSL has to be used on Windows.

Make sure to have SSH access to the managed nodes with your SSH private key.

Create a `inventory.yml` file that contains the IP addresses of the replicas:

```yaml
replicas:
  hosts:
    replica0:
      ansible_user: ubuntu
      ansible_host: 192.168.178.10
    replica1:
      ansible_user: ubuntu
      ansible_host: 192.168.178.11
    replica2:
      ansible_user: ubuntu
      ansible_host: 192.168.178.12
    replica3:
      ansible_user: ubuntu
      ansible_host: 192.168.178.13
```

Then, install the required dependencies with the `ubuntu-playbook.yml` playbook.
In some cases, the stock image only provides a `root` user. This image creates
an `ubuntu` user with the same authorized public key as the root user for SSH
login. The `inventory.root.yml` file is like the inventory file above, but with
`ansible_user: root`.

```shell
ansible-playbook -i inventory.root.yml ubuntu-playbook.yml --private-key ~/.ssh/my_custom_key
```

**Update the IP addresses in the `config/hosts.config` file to your replica IP
addresses.**

Run `./gradlew installDist` script to build ISOS and copy the required files to
the `build/install/library` directory. This directory is synchronized to the
replicas via ansible.

To build ISOS, install its dependencies and deploy the current build, run the
following commands. They have to be repeated every time you do a code change:

```shell
./gradlew installDist && \
    ansible-playbook -i inventory.yml benchmark-playbook.yml --private-key ~/.ssh/my_custom_key
```

### KV Store

Four replicas locally with tmux:

```shell
./quad-replica.sh smartrun.sh isos.benchmark.kvstore.KVStoreReplica
```

Four SSH sessions automatically with tmux:

```shell
./ssh-quad-replica.sh smartrun.sh isos.benchmark.kvstore.KVStoreReplica ubuntu@host0 ubuntu@host1 ubuntu@host2 ubuntu@host3
```

One replica in one of each region, manually:

```shell
./smartrun.sh isos.benchmark.kvstore.KVStoreReplica 0
```

When running the script manually, be sure to use the correct replicaID that is

#### Latency

Client (one instance per region):

```shell
./client.sh isos.benchmark.latency.KVStoreLatencyBenchmark \
  --groupId=1 \
  --clientCount=5 \
  --requestCount=10 \
  --writeRatioPercent=10 \
  --conflictRatioPercent=10 \
  --outputDir="/home/ubuntu/benchmark_out/" \
  --benchmarkName="Optimized_Ver1"
```

#### Throughput

Client (one instance per region):

```shell
./client.sh isos.benchmark.latency.KVStoreThroughputBenchmark \
  --groupId=1 \
  --clientCount=5 \
  --requestCount=10 \
  --writeRatioPercent=10 \
  --conflictRatioPercent=10 \
  --outputDir="/home/ubuntu/benchmark_out/" \
  --benchmarkName="Optimized_Ver1"
```

### YCSB

First, build the project and copy the files using the `copy_library.sh` script
for local running or the `benchmark-playbook.yml` for remote running.

#### Remote Replicas and clients

Run the YCSB replicas in each region:

```shell
./ssh-quad-replica.sh replica_ycsb_isos.sh isos.benchmark.ycsb.IsosYcsbServer ubuntu@host0 ubuntu@host1 ubuntu@host2 ubuntu@host3
```

Then, run a client node in each region with the following command:

```shell
./ssh-quad-replica.sh client_ycsb_isos.sh isos.benchmark.ycsb.IsosYcsbClient isos_95r_5w trivial_implementation ubuntu@client0 ubuntu@client1 ubuntu@client2 ubuntu@client3
```

Meaning of arguments:

1. YCSB Script name
2. YCSB Java Class Binding
3. Workload name from `config/ycsb_workloads/` directory
4. Benchmark name (for result collection)
5. Remaining four arguments: Username and Hostname for remote client nodes

#### Local remote and clients

```shell
./quad-replica.sh replica_ycsb_isos.sh isos.benchmark.ycsb.IsosYcsbServer
```

Then SSH into a client VPS and run the YCSB client command manually:

```shell
./client_ycsb_isos.sh isos.benchmark.ycsb.IsosYcsbClient isos_95r_5w trivial_implementation {CLIENT_ID}
```

### Collecting Benchmark Results

Collecting the benchmark results into the `evaluation/data` directory is done
with an Ansible playbook as well:

```shell
ansible-playbook -i inventory.yml collect-playbook.yml --private-key ~/.ssh/my_custom_key
```

## Evaluation

Evaluation is done with a Jupyter Notebook written in Python.

```shell
cd evaluation
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
```

Select the new virtual env and run the commands in the Jupyter notebook.

## Documentation of Thread Names

- `SCommS`: ServerCommunicationSystem
- `SCommL`: ServerCommunicationLayer

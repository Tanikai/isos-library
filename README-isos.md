# ISOS

## About

This repository contains an implementation of ISOS, based on the BFT-SMaRt
networking stack.

## Development

### Tooling

- Formatting: `google-java-format`

## Running

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
class name:

```shell
./client.sh isos.benchmark.kvstore.KVStoreClientInteractive
```

## Configuration

TODO: configuration values for ISOS

## Benchmarking

### YCSB

First, build and copy files into directories with:

```shell
./copy-library
```

Then, run the YCSB database replicas with:

```shell
./quad-replica.sh replica_ycsb_isos.sh isos.benchmark.ycsb.IsosYcsbServer
```

Run the YCSB client with:

```shell
./ycsb_client.sh isos.benchmark.ycsb.IsosYcsbClient isos_1
```

## Documentation of Thread Names

- `SCommS`: ServerCommunicationSystem
- `SCommL`: ServerCommunicationLayer

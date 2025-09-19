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

./quad-replica.sh bftsmart.demo.messaging.MessagingReplica
```

To exit, press Ctrl+C multiple times, or `tmux action key` + `&`, then confirm
with `y`.

The client can then be run with the `client.sh` script together with the Java
class name:

```shell
./client.sh isos.examples.MessagingExampleClient
```

## Configuration


## Debugging

```bash
 ./gradlew build && ./copy_library.sh && ./quad-replica.sh
```

## Documentation of Thread Names

- `SCommS`: ServerCommunicationSystem
- `SCommL`: ServerCommunicationLayer

# ISOS

## About

This repository contains an implementation of ISOS, based on the BFT-SMaRt
networking stack.

## Development

### Tooling

- Formatting: `google-java-format`


## Running

To run four replicas (without clients), use the `quad-replica.sh` script (`tmux` required):

```bash
./quad-replica.sh
```

To exit, press Ctrl+C multiple times, or `tmux action key` + `&`, then confirm with `y`.

## Debugging

```bash
 ./gradlew build && ./copy_library.sh && ./quad-replica.sh
```

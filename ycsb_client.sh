#!/bin/bash

if [ $# -lt 2 ]; then
  echo "Usage: $0 <classpath> <workload name>"
  echo "Example: $0 isos.benchmark.ycsb.IsosYcsbClient isos_95r_5w"
  exit 1
fi

DIR="./clients/c"
SCRIPTNAME="client_ycsb_isos.sh"
CLASSNAME="$1"
WORKLOADNAME="$2"

cd $DIR

./$SCRIPTNAME $CLASSNAME $WORKLOADNAME

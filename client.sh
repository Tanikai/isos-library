#!/bin/bash

if [ -z "$1" ]; then
  echo "Usage: $0 <classpath>"
  echo "Example: $0 isos.examples.MessagingExampleClient"
  exit 1
fi

DIR="./clients/c"
SCRIPTNAME="smartrun.sh"
CLASSNAME="$1"

cd $DIR

./$SCRIPTNAME $CLASSNAME 0 # 0 is id of client

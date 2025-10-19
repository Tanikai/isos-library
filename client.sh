#!/bin/bash

if [ -z "$1" ]; then
  echo "Usage: $0 <classpath> <clientId>"
  echo "Example: $0 isos.examples.MessagingExampleClient 0"
  exit 1
fi

DIR="./clients/c"
SCRIPTNAME="smartrun.sh"
CLASSNAME="$1"

shift # Skip the classname

cd $DIR

./$SCRIPTNAME $CLASSNAME "${@}"

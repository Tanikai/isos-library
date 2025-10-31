# Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the @author tags
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

#/bin/bash

CLASSNAME="$1"
WORKLOAD="$2"
BENCHMARK_NAME="$3"
CLIENT_ID="$4"

echo "Create benchmark out directory..."

mkdir -p "/home/ubuntu/benchmark_out/ycsb_${BENCHMARK_NAME}/"

# Each instance of YCSB can have up to 10.000 client IDs
CLIENT_ID_START=$((CLIENT_ID * 10000))

java  -Djava.security.properties="./config/java.security" \
  -Dlogback.configurationFile="./config/logback.xml" \
  -cp ./lib/*:./bin/ com.yahoo.ycsb.Client \
  -threads 15 \
  -P "config/ycsb_workloads/$WORKLOAD" \
  -p measurementtype=timeseries \
  -p exportfile="/home/ubuntu/benchmark_out/ycsb_${BENCHMARK_NAME}/ycsb_${BENCHMARK_NAME}_${CLIENT_ID}.csv" \
  -p smart-initkey="$CLIENT_ID_START" \
  -p timeseries.granularity=1000 \
  -db $CLASSNAME \
  -s

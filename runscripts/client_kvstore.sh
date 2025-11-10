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

args=("$@")
count=${#args[@]}
middle_args=("${args[@]:0:count-1}")
SCRIPT_ARGS="${middle_args[*]}"
# ClientID is last argument -> has to be passed to "--groupId=..."
client_id=("${args[@]: -1}")

echo "Benchmark Args: $middle_args"
echo "Client Id: $client_id"

./smartrun.sh \
  $SCRIPT_ARGS \
  --groupId="$client_id"

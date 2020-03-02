#!/usr/bin/env bash

#Licensed to the Apache Software Foundation (ASF) under one or more contributor license
#agreements. See the NOTICE file distributed with this work for additional information regarding
#copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
#"License"); you may not use this file except in compliance with the License. You may obtain a
#copy of the License at
#
#http://www.apache.org/licenses/LICENSE-2.0
#
#Unless required by applicable law or agreed to in writing, software distributed under the License
#is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
#or implied. See the License for the specific language governing permissions and limitations under
#the License.

TEST_RUN_COUNT=10
COMMAND_REPETITION_COUNT=100000
REDIS_HOST=localhost
REDIS_PORT=6379
FILE_PREFIX=$(git rev-parse --short HEAD)

while getopts ":t:c:h:p:n:" opt; do
  case ${opt} in
  t)
    TEST_RUN_COUNT=${OPTARG}
    ;;
  c)
    COMMAND_REPETITION_COUNT=${OPTARG}
    ;;
  n)
    FILE_PREFIX=${OPTARG}
    ;;
  h)
    REDIS_HOST=${OPTARG}
    ;;
  p)
    REDIS_PORT=${OPTARG}
    ;;
  \?)
    echo "Usage: ${0} [-h host] [-p port] [-t (test run count)] [-c (command repetition count)]"
    ;;
  :)
    echo "Invalid option: $OPTARG requires an argument" 1>&2
    exit 1
    ;;
  esac
done


redis_benchmark_commands=("SET" "GET" "INCR" "LPUSH" "RPUSH" "LPOP" "RPOP" "SADD" "SPOP")

function aggregate() {
  local command=$1

  grep ${command} results.csv | cut -d"," -f 2 | cut -d"\"" -f 2 | awk '{ sum += $1 } END { if (NR > 0) print sum / NR }'
}

function join_by() {
  local IFS="$1"
  shift
  echo "$*"
}

REDIS_COMMAND_STRING=$(join_by , "${redis_benchmark_commands[@]}")

SCRIPT_DIR=$(
  cd $(dirname $0)
  pwd
)

cd ${SCRIPT_DIR}

rm -f results.csv

X=0
while [[ ${X} -lt ${TEST_RUN_COUNT} ]]; do
  echo "Run " ${X} " of " ${TEST_RUN_COUNT}
  redis-benchmark -h ${REDIS_HOST} -p ${REDIS_PORT} -t ${REDIS_COMMAND_STRING} -q -n ${COMMAND_REPETITION_COUNT} -r 32767 --csv >>results.csv

  ((X = X + 1))
done

AGGREGATE_FILE_NAME=${FILE_PREFIX}-aggregate.csv

echo "Command", "Average Requests Per Second" >${AGGREGATE_FILE_NAME}
for command in ${redis_benchmark_commands[@]}; do
  SUM_AGGREGATE=$(aggregate ${command})
  echo ${command}, ${SUM_AGGREGATE} >>${AGGREGATE_FILE_NAME}
done

echo "Results saved to " ${AGGREGATE_FILE_NAME}
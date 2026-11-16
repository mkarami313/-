#!/bin/bash
# usage: runsuite.sh test_x.py  — restarts the TEST server (8151) then runs one suite
cd /home/rasta/hamrah-v2test || exit 1
fuser -k 8151/tcp >/dev/null 2>&1
for i in $(seq 1 20); do fuser 8151/tcp >/dev/null 2>&1 || break; sleep 0.5; done
(setsid nohup timeout 3h python3 hamrah-api.py > run.log 2>&1 &)
for i in $(seq 1 40); do curl -sf http://127.0.0.1:8151/api/health >/dev/null && break; sleep 0.5; done
BASE=http://127.0.0.1:8151 timeout 900 python3 "$1" 2>&1 | tail -40

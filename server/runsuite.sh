#!/bin/bash
# usage: runsuite.sh test_x.py — (re)starts the TEST server (8151) as a transient systemd unit, runs one suite, stops it.
# No kill/fuser on this production host: the test server lives in its own unit and is stopped with systemctl.
cd /home/rasta/hamrah-v2test || exit 1
UNIT=hamrah-v2test-run
systemctl stop "$UNIT" 2>/dev/null; systemctl reset-failed "$UNIT" 2>/dev/null
systemd-run --unit="$UNIT" --working-directory=/home/rasta/hamrah-v2test --property=RuntimeMaxSec=3h python3 hamrah-api.py >/dev/null
for i in $(seq 1 40); do curl -sf http://127.0.0.1:8151/api/health >/dev/null && break; sleep 0.5; done
BASE=http://127.0.0.1:8151 timeout 900 python3 "$1" 2>&1 | tail -40
systemctl stop "$UNIT"

#!/bin/bash
DEV="${1:-6c49ca35}"
PS4="${2:-192.168.1.152}"

adb -s $DEV shell "chmod +x /data/tshark && /data/tshark -f 'udp and host $PS4' -w /data/kip.pcapng"
adb -s $DEV pull /data/kip.pcapng docs/kip-$(date +%s).pcapng

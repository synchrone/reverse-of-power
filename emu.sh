#!/bin/bash

cat <<EOF | while read packet; do
8a33ffffffff0000000000000000000000000000000000000000000000000000000000000000
8a33ffffffff0000000000000000000000000000000000000000000000000000000000000000
0c89e8846103f469200000006232663366386562306366346566346232333539383731643335343935323235
EOF
   echo -n "$packet" | xxd -r -p #| nc -u 192.168.0.8 9060
done

ae 7f
1a
00 00 00 01
00 00 00 00 00 00 00 45
00 00 00 00 00 00 00 33
29
b1 e2 ff ff # always b1e2ffff
3b 00 00 00 # length, big-endian
{"TypeString":"SessionStateMessage","SessionID":1838788817}


ae 7f
1b # msg id
00 00 00 01
00 00 00 00 00 00 00 85
00 00 00 00 00 00 00 33
29
b1 e2 ff ff
7b 00 00 00
# then 123b of JSON (0x7b), no trailer
{"TypeString":"AssignPlayerIDAndSlotMessage","PlayerID":5,"SlotID":0,"UDPPortOffset":0,"DisplayName":"Player 1","PSNID":""}


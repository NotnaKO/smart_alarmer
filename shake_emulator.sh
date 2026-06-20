#!/bin/bash
# shake_emulator.sh
# Simulates physical shake of the Android emulator using the telnet console.

TOKEN=$(cat ~/.emulator_console_auth_token 2>/dev/null)
if [ -z "$TOKEN" ]; then
  echo "Error: No emulator console auth token found in ~/.emulator_console_auth_token"
  exit 1
fi

PORT=5554
echo "Shaking emulator on port $PORT..."

# Loop 35 times (enough to trigger 30 shakes)
for i in {1..35}
do
  if [ $((i % 2)) -eq 0 ]; then
    VAL="25:9.8:0"
  else
    VAL="-25:9.8:0"
  fi
  (echo "auth $TOKEN"; echo "sensor set acceleration $VAL"; echo "quit") | nc -w 1 localhost $PORT > /dev/null
  sleep 0.15
done

# Reset to default gravity
(echo "auth $TOKEN"; echo "sensor set acceleration 0:9.8:0"; echo "quit") | nc -w 1 localhost $PORT > /dev/null

echo "Shake simulation complete!"

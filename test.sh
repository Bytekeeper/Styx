#!/usr/bin/env bash
docker rm -vf $(docker ps -a -f name=GAME -q)
rm -rf ~/.scbw/games/GAME_test
cp build/libs/FFT_TER-all-1.0-SNAPSHOT.jar ~/.scbw/bots/StyxZ/AI/
nohup scbw.play --headless --bots StyxZ McRaveZ --timeout_at_frame 43200 --game_name test&
sleep 5
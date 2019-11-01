#!/usr/bin/env bash
docker rm -vf $(docker ps -a -f name=GAME -q)
rm -rf ~/.scbw/games/GAME_blub
cp build/libs/FFT_TER-all-1.0-SNAPSHOT.jar ~/.scbw/bots/StyxZ/AI/
# nohup scbw.play --headless --bots StyxZ "Bryan Weber" --timeout_at_frame 43200 --game_name blub&
# nohup scbw.play --headless --bots StyxZ "McRaveZ" --timeout_at_frame 43200 --game_name blub&
# nohup scbw.play --headless --bots StyxZ MadMixT --timeout_at_frame 43200 --game_name blub&
# nohup scbw.play --headless --bots StyxZ "PurpleSwarm" --timeout_at_frame 43200 --game_name blub&
# nohup scbw.play --headless --bots StyxZ "lol" --timeout_at_frame 43200 --game_name blub&
# nohup scbw.play --headless --bots StyxZ "Simon Prins" --timeout_at_frame 43200 --game_name blub&
# nohup scbw.play --headless --bots StyxZ Locutus --timeout_at_frame 43200 --game_name blub&
# nohup scbw.play --headless --bots StyxZ Antiga --timeout_at_frame 43200 --game_name blub&
# nohup scbw.play --headless --bots StyxZ 'Chris Coxe' --timeout_at_frame 43200 --game_name blub&
#nohup scbw.play --headless --bots StyxZ "Stone" --timeout_at_frame 43200 --game_name blub&
nohup scbw.play --headless --bots StyxZ "CUBOT" --timeout_at_frame 43200 --game_name blub&
# nohup scbw.play --headless --bots StyxZ tscmoo --timeout_at_frame 43200 --game_name blub&
sleep 5
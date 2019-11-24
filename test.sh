#!/usr/bin/env bash
docker rm -vf $(docker ps -a -f name=GAME -q)
rm -rf ~/.scbw/games/GAME_shark
cp build/libs/FFT_TER-all-1.0-SNAPSHOT.jar ~/.scbw/bots/StyxZ/AI/

function play() {
  (
    trap "" HUP
    echo 'START'
    scbw.play --headless --bots StyxZ "$1" --timeout_at_frame 43200 --game_name shark
    cp ~/.scbw/games/GAME_shark/write_0/trace.json ~/cherryvis-docker/replays/player_0.rep.cvis
    cp ~/.scbw/games/GAME_shark/player_0.rep ~/cherryvis-docker/replays/
    echo 'DONE'
  ) &
}
#nohup scbw.play --headless --bots StyxZ "Bryan Weber" --timeout_at_frame 43200 --game_name shark&
#nohup scbw.play --headless --bots StyxZ "McRaveZ" --timeout_at_frame 43200 --game_name shark&
# nohup scbw.play --headless --bots StyxZ MadMixT --timeout_at_frame 43200 --game_name shark&
#nohup scbw.play --headless --bots StyxZ "PurpleSwarm" --timeout_at_frame 43200 --game_name shark&
# nohup scbw.play --headless --bots StyxZ "lol" --timeout_at_frame 43200 --game_name shark&
# nohup scbw.play --headless --bots StyxZ "Simon Prins" --timeout_at_frame 43200 --game_name shark&
#nohup scbw.play --headless --bots StyxZ Locutus --timeout_at_frame 43200 --game_name shark&
# nohup scbw.play --headless --bots StyxZ Antiga --timeout_at_frame 43200 --game_name shark&
#nohup scbw.play --headless --bots StyxZ 'Chris Coxe' --timeout_at_frame 43200 --game_name shark&
#nohup scbw.play --headless --bots StyxZ "Stone" --timeout_at_frame 43200 --game_name shark&
 #nohup scbw.play --headless --bots StyxZ tscmoo --timeout_at_frame 43200 --game_name shark&
# nohup scbw.play --headless --bots StyxZ WuliBot --timeout_at_frame 43200 --game_name shark&
#nohup scbw.play --headless --bots StyxZ "Andrew Smith" --timeout_at_frame 43200 --game_name shark&
#play CUBOT
#play Stone
 #play McRaveZ
# play 'Chris Coxe'
# play Antiga
# play Locutus
#play Proxy
# play BananaBrain
# play tscmoo
# play WuliBot
#play 'Iron bot'
play TyrProtoss
# play McRave
# play ZurZurZur
# play Flash
# play 'Simon Prins'
# play 'Andrew Smith'
#play WillBot
# play Crona
# play Ecgberht
# play Microwave
sleep 5

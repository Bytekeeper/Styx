#!/usr/bin/env bash
docker rm -vf $(docker ps -a -f name=GAME -q)
cp build/libs/FFT_TER-all-1.0-SNAPSHOT.jar ~/.scbw/bots/StyxZ/AI/
mapfile -t maps < <(find ~/.scbw/maps/sscai -name '*.sc?' -printf "sscai/%P\n")
cp ~/.scbw/bots/StyxZ/botd.json ~/.scbw/bots/StyxZ/bot.json
#cp ~/.scbw/bots/StyxZ/botb.json ~/.scbw/bots/StyxZ/bot.json

function play() {
  (
    trap "" HUP
    echo 'START'
    game_name=${1// /_}
    rm -rf ~/.scbw/games/GAME_$game_name
    map=${maps[$RANDOM % ${#maps[@]} ]}
    echo "Playing vs $1 on $map"
    scbw.play --headless --read_overwrite --bots StyxZ "$1" --timeout_at_frame 28000 --game_name $game_name --map "$map"
    mkdir ~/cherryvis-docker/replays/$game_name.rep.cvis
    cp ~/.scbw/games/GAME_$game_name/write_0/trace.json ~/cherryvis-docker/replays/$game_name.rep.cvis/
    cp ~/.scbw/games/GAME_$game_name/player_0.rep ~/cherryvis-docker/replays/$game_name.rep
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
#play 'Chris Coxe'
# play Antiga
#play BananaBrain
# play tscmoo
#play tscmoop2
# play WuliBot
#play TyrProtoss
#play 'Tomas Vajda'
#play McRave
#play ZurZurZur
# play Flash
#play 'Simon Prins'
#play Bereaver
#play WillBot
#play Crona
#play Ecgberht
#play Microwave
#play ZNZZBot
# play GuiBot
#play AntigaZ
#play Feint
#play PurpleSwarm
#play Dragon
#play WillyT

# Top 12
#play krasi0
play Locutus
#play PurpleWave
#play 'Hao Pan'
#play Microwave
#play 'Iron bot'
#play Steamhammer
#play 'Marian Devecka'
#play ChimeraBot
#play Antiga
#play Proxy
sleep 5

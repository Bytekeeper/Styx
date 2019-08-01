package org.styx

import bwapi.BWClient
import bwapi.DefaultBWListener
import bwapi.Game
import bwem.BWEM
import org.styx.task.Gathering

class Listener : DefaultBWListener() {
    private lateinit var game: Game
    private val client = BWClient(this)

    private val tasks = listOf(Gathering)

    override fun onStart() {
        game = client.game
        Styx.game = game
        val bwem = BWEM(game)
        bwem.initialize()
        Styx.map = bwem.map
    }

    override fun onFrame() {
        Styx.update()

        tasks.sortedByDescending { it.utility }.forEach { it.execute() }
    }

    fun start() {
        client.startGame()
    }
}

fun main(vararg arg: String) {
    Listener().start();
}
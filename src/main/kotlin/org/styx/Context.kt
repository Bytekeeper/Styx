package org.styx

import bwapi.Game
import bwapi.Player
import bwem.map.Map
import org.styx.`fun`.BouncingLines
import org.styx.actions.ActionBoards

object Context {
    var frameCount = -1
    var turnSize = 0
    lateinit var game: Game
    lateinit var self: Player
    lateinit var map: Map
    val find = UnitQueries()
    val economy = Economy()
    val reserve = Reservation()
    val taskProviders = TaskProviders()
    val actionBoards = ActionBoards()
    val geography = Geography()
    val command = Command()
    val recon = Reconnaissance()
    val strategy = Strategy()
    val visualization = Visualization()
    val bouncingLines = BouncingLines()

    fun reset() {
        frameCount = -1
        turnSize = 0

        find.reset()
        economy.reset()
        reserve.reset()
        actionBoards.reset()
        recon.reset()
        bouncingLines.reset()
    }

    fun update() {
        find.update()
        economy.update()
        reserve.update()
        actionBoards.update()
        recon.update()
//        bouncingLines.update()
    }
}
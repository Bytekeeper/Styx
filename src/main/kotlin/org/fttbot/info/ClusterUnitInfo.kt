package org.fttbot.info

import org.fttbot.behavior.Attacking
import org.fttbot.div
import org.fttbot.estimation.CombatEval
import org.fttbot.estimation.SimUnit
import org.fttbot.plus
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.unit.*

object ClusterUnitInfo {
    private val unitInfo = HashMap<Cluster<*>, ClusterInfo>()

    fun step() {
        unitInfo.clear()
    }

    fun getInfo(cluster: Cluster<*>) = unitInfo.computeIfAbsent(cluster) { ClusterInfo(cluster) }
}

class ClusterInfo(private val cluster: Cluster<*>) {
    val unitsInArea by lazy {
        (UnitQuery.unitsInRadius(cluster.position, 600) +
                EnemyState.seenUnits.filter { it.getDistance(cluster.position) < 600 })
                .filter { it is PlayerUnit && (it is Armed || it is Bunker) && it.isCompleted }
                .map { it as PlayerUnit }
    }

    val combatRelevantUnits by lazy { unitsInArea.filter { it.getDistance(forceCenter) < 300 } }

    val forceCenter by lazy {
        unitsInArea.fold(Position(0, 0)) { a, u -> a + u.position } / unitsInArea.size
    }

    val combatEval: Double by lazy {
        val enemyUnits = combatRelevantUnits.filter { it.isEnemyUnit }.map { SimUnit.of(it) }

        CombatEval.probabilityToWin(
                combatRelevantUnits.filter { it.isMyUnit && (it !is MobileUnit || !it.isWorker || it.board.goal is Attacking) }.map { SimUnit.of(it) }, enemyUnits)

    }

    val needDetection: Double by lazy {
        if (combatRelevantUnits.none { it.isEnemyUnit && (it is Armed && it is Cloakable && it.isCloaked || it is Lurker && it.isBurrowed) && !it.isDetected }) 0.0
        else combatEval
    }
}
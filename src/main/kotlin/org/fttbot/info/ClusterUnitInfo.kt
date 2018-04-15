package org.fttbot.info

import org.fttbot.LazyOnFrame
import org.fttbot.div
import org.fttbot.estimation.CombatEval
import org.fttbot.estimation.SimUnit
import org.fttbot.plus
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.unit.*

class ClusterCombatInfo(private val cluster: Cluster<*>) {
    val unitsInArea by LazyOnFrame {
        (UnitQuery.inRadius(cluster.position, 600) +
                EnemyInfo.seenUnits.filter { it.getDistance(cluster.position) < 600 })
                .filterIsInstance(PlayerUnit::class.java)
    }

    val combatRelevantUnits by LazyOnFrame {
        unitsInArea.filter { it.getDistance(forceCenter) < 300 }
                .filter { (it !is Worker || it.isAttacking) && (it is Attacker || it is Bunker) && it.isCompleted }
    }

    val enemyUnits: List<PlayerUnit> by LazyOnFrame { combatRelevantUnits.filter { it.isEnemyUnit } }
    val myUnits: List<PlayerUnit> by LazyOnFrame { combatRelevantUnits.filter { it.isMyUnit } }

    val forceCenter by LazyOnFrame {
        val enemies = unitsInArea.filter { it.isEnemyUnit }
        val mine = unitsInArea.filter { it.isMyUnit }
        enemies.fold(Position(0, 0)) { a, u -> a + u.position }.multiply(Position(3, 3))
                .add(mine.fold(Position(0, 0)) { a, u -> a + u.position }).div(enemies.size * 3 + mine.size)
    }

    val attackEval: Double by LazyOnFrame {
         CombatEval.probabilityToWin(myUnits.filter { it is MobileUnit }.map { SimUnit.of(it) }, enemyUnits.map { SimUnit.of(it) })
    }

    val defenseEval: Double by LazyOnFrame {
        val enemyUnits = combatRelevantUnits.filter { it.isEnemyUnit }.map { SimUnit.of(it) }
        val myUnits = combatRelevantUnits.filter { it.isMyUnit }.map { SimUnit.of(it) }
        val result = CombatEval.probabilityToWin(myUnits, enemyUnits)
        result
    }

    val needDetection: Double by LazyOnFrame {
        if (combatRelevantUnits.none { it.isEnemyUnit && (it is Attacker && it is Cloakable && it.isCloaked || it is Lurker && it.isBurrowed) && !it.isDetected }) 0.0
        else attackEval
    }

    companion object {
        private val unitInfo by LazyOnFrame { HashMap<Cluster<*>, ClusterCombatInfo>() }

        fun step() {
            unitInfo.clear()
        }

        fun getInfo(cluster: Cluster<*>) = unitInfo.computeIfAbsent(cluster) { ClusterCombatInfo(cluster) }
    }
}
package org.styx.micro

import bwapi.BulletType
import bwapi.Position
import bwapi.UnitType
import org.bk.ass.bt.*
import org.locationtech.jts.math.Vector2D
import org.styx.*
import org.styx.Styx.game
import org.styx.Styx.geography
import org.styx.Styx.units
import org.styx.action.BasicActions

class WithTarget<T>(
        val actor: SUnit,
        var target: T? = null
)

class Attack(private val attacker: SUnit,
             private val enemy: SUnit) : BehaviorTree() {
    private val board: WithTarget<SUnit> = WithTarget(attacker, enemy)
    private val kiteEnemy = Sequence(
            Condition {
                val enemyRangeVsUs = enemy.maxRangeVs(attacker)
                val ourRangeVsEnemy = attacker.maxRangeVs(enemy)
                attacker.couldTurnAwayAndBackBeforeCooldownEnds(enemy) &&
                        (ourRangeVsEnemy > enemyRangeVsUs || ourRangeVsEnemy == enemyRangeVsUs && attacker.topSpeed > enemy.topSpeed) &&
                        enemy.inAttackRange(attacker, 64) &&
                        attacker.inAttackRange(enemy, -16)
            },
            EvadeEnemy(board)
    )

    private val attackEnemy = Sequence(
            Condition {
                val relativeMovement = attacker.velocity.normalize().dot(enemy.velocity.normalize())
                relativeMovement < -0.3 ||
                        attacker.inAttackRange(enemy) ||
                        enemy.distanceTo(attacker) < attacker.maxRangeVs(enemy) + 16 ||
                        !attacker.flying && !geography.walkRay.noObstacle(attacker.walkPosition, enemy.walkPosition)
            },
            NodeStatus.RUNNING.after {
                if (attacker.flying && !attacker.inAttackRange(enemy, 8)) {
                    val force = Potential.intercept(attacker, enemy)
                    Potential.apply(attacker, force)
                } else {
                    BasicActions.attack(attacker, enemy)
                }
            }
    )

    private val evadeOtherEnemies = Sequence(
            Condition { attacker.couldTurnAwayAndBackBeforeCooldownEnds(enemy) && enemy.engaged.isNotEmpty() },
            LambdaNode {
                val evadeTo = (0..8).asSequence()
                        .map {
                            Vector2D(64.0, 0.0).rotate(it * PI2 / 8)
                                    .toPosition()
                                    .toWalkPosition() + attacker.walkPosition
                        }.filter {
                            enemy.distanceTo(it.toPosition(), attacker.unitType) <= attacker.maxRangeVs(enemy) &&
                                    (attacker.flying || geography.walkRay.noObstacle(attacker.walkPosition, it))

                        }.map { wp ->
                            val position = wp.middlePosition()
                            val futureThreats = units.enemy.inRadius(position.x, position.y, 400) { it == enemy || it.target == attacker && it.distanceTo(position, attacker.unitType) <= it.maxRangeVs(attacker) }
                            position to futureThreats.size
                        }.minBy { it.second }
                if (evadeTo != null && evadeTo.second < attacker.engaged.size) {
                    BasicActions.move(attacker, evadeTo.first)
                    NodeStatus.RUNNING
                } else
                    NodeStatus.FAILURE
            })

    private val homeInToEnemy = Sequence(
            Condition {
                attacker.flying && enemy.weaponAgainst(attacker).minRange() > 0 ||
                        attacker.irridiated ||
                        enemy.unitType.canMove() && (enemy.maxRangeVs(attacker) > attacker.maxRangeVs(enemy) && attacker.maxRangeVs(enemy) - attacker.distanceTo(enemy) < 8)

            },
            Intercept(board)
    )

    private val findEnemy = Sequence(
            Condition { !enemy.visible },
            NodeStatus.RUNNING.after {
                BasicActions.move(attacker, enemy.position)
            }
    )

    override fun getRoot(): TreeNode = Selector(
            findEnemy,
            EvadeScarabs(attacker),
            StormDodge(attacker),
            Sequence(
                    Condition { attacker.isOnCoolDown && attacker.canMoveWithoutBreakingAttack },
                    Selector(
                            SpreadOut(attacker),
                            homeInToEnemy,
                            evadeOtherEnemies,
                            kiteEnemy,
                            BetterAltitude(board)
                    )
            ),
            attackEnemy,
            Intercept(board)
    )
}

class StormDodge(private val unit: SUnit) : BehaviorTree() {
    private val board = WithTarget<Position>(unit)

    override fun getRoot(): TreeNode = Sequence(
            SelectTarget(board) {
                game.bullets.firstOrNull { it.type == BulletType.Psionic_Storm && it.position.getDistance(unit.position) <= 72 }?.position
            },
            EvadePosition(board)
    )

}

class EvadeScarabs(private val unit: SUnit) : BehaviorTree() {
    private val board = WithTarget<SUnit>(unit)

    override fun getRoot(): TreeNode = Sequence(
            Condition { !unit.flying },
            SelectTarget(board) {
                units.enemy.nearest(unit.x, unit.y, 128) { it.unitType == UnitType.Protoss_Scarab }
            },
            Condition {
                board.target!!.distanceTo(board.actor) < 64
            },
            EvadeEnemy(board)
    )

}

class Stop(private val unit: SUnit) : TreeNode() {
    override fun exec() {
        unit.stop()
        running()
    }
}

class KeepDistance(private val unit: SUnit) : TreeNode() {
    override fun exec() {
        val force = Potential.avoidDanger(unit, 96) +
                Potential.collisionRepulsion(unit) * 0.2
        Potential.apply(unit, force)
        running()
    }
}

class EvadeEnemy(private val board: WithTarget<SUnit>) : TreeNode() {
    override fun exec() {
        val unit = board.actor
        val other = board.target ?: run {
            failed()
            return
        }
        var force = Potential.repelFrom(unit, other)
        if (!board.actor.flying)
            force += Potential.collisionRepulsion(unit) * 0.3
        Potential.apply(unit, force)
        running()
    }
}

class EvadePosition(private val board: WithTarget<Position>) : TreeNode() {
    override fun exec() {
        val unit = board.actor
        val position = board.target ?: run {
            failed()
            return
        }
        var force = Potential.repelFrom(unit, position)
        if (!board.actor.flying)
            force += Potential.collisionRepulsion(unit) * 0.3
        Potential.apply(unit, force)
        running()
    }

}

class SelectTarget<T>(private val board: WithTarget<T>, private val selector: () -> T?) : TreeNode() {
    override fun exec() {
        board.target = selector()
        if (board.target == null) {
            failed()
            return
        }
        success()
    }
}

class Intercept(private val board: WithTarget<SUnit>) : TreeNode() {
    override fun exec() {
        val target = board.target ?: run {
            failed()
            return
        }
        var force = Potential.intercept(board.actor, target)
        if (!board.actor.flying)
            force += Potential.collisionRepulsion(board.actor) * 0.2

        Potential.apply(board.actor, force)
        running()
    }
}

class SpreadOut(private val unit: SUnit) : TreeNode() {
    override fun exec() {
        if (unit.threats.any { it.weaponAgainst(unit).isSplash }) {
            val force = Potential.collisionRepulsion(unit)
            if (force.lengthSquared() > 0) {
                Potential.apply(unit, force)
                running()
            } else {
                failed()
            }
        } else {
            failed()
        }
    }
}

class BetterAltitude(private val board: WithTarget<SUnit>) : TreeNode() {
    override fun exec() {
        val target = board.target ?: run {
            failed()
            return
        }
        if (board.actor.flying
                || target.flying
                || board.actor.maxRangeVs(target) <= 32
                || board.actor.altitude > target.altitude) {
            failed()
        } else {
            val bestTile = board.actor.tilePosition.adjacentTiles(1)
                    .filter { it.altitude > target.altitude && target.distanceTo(it.closestTo(board.actor.position), board.actor.unitType) <= board.actor.maxRangeVs(target) }
                    .maxBy { it.altitude } ?: run {
                failed()
                return
            }
            val force = Potential.collisionRepulsion(board.actor) +
                    Potential.reach(board.actor, bestTile.closestTo(board.actor.position))
            Potential.apply(board.actor, force)
            running()
        }
    }

}
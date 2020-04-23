package org.styx.micro

import bwapi.BulletType
import bwapi.Position
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
                enemy.hasWeaponAgainst(attacker) &&
                        attacker.couldTurnAwayAndBackBeforeCooldownEnds(enemy) &&
                        (ourRangeVsEnemy > enemyRangeVsUs || ourRangeVsEnemy == enemyRangeVsUs && attacker.topSpeed > enemy.topSpeed) &&
                        enemy.inAttackRange(attacker, 64) &&
                        attacker.inAttackRange(enemy, -8)
            },
            EvadeEnemy(board)
    )
    private val airCircle = Sequence(
            Condition {
                attacker.flying && attacker.velocity.lengthSquared() < attacker.topSpeed * attacker.topSpeed * 0.7
            },
            NodeStatus.RUNNING.after {
                val vel = (if (attacker.velocity.lengthSquared() < 0.01) Vector2D(1.0, 0.0) else attacker.velocity.normalize()) * 32.0
                val x = -vel.y
                val y = vel.x
                val pos = Position(
                        x.toInt(),
                        y.toInt()
                )
                val repelFrom = (attacker.position + pos).makeValid()
                val force = Potential.repelFrom(attacker, repelFrom)
                Potential.apply(attacker, force)
            }
    )

    private val attackEnemy = Sequence(
            Condition {
                attacker.inAttackRange(enemy) ||
                        enemy.distanceTo(attacker) < attacker.maxRangeVs(enemy) + 16 ||
                        !attacker.flying && !attacker.noObstacle(enemy.walkPosition)
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
                        .flatMap { dir ->
                            sequenceOf(
                                    Vector2D(32.0, 0.0).rotate(dir * PI2 / 8),
                                    Vector2D(64.0, 0.0).rotate(dir * PI2 / 8),
                                    Vector2D(96.0, 0.0).rotate(dir * PI2 / 8)
                            ).map {
                                (it.toPosition()
                                        .toWalkPosition() + attacker.walkPosition).makeValid()
                            }
                        }.filter {
                            enemy.distanceTo(it.toPosition(), attacker.unitType) <= attacker.maxRangeVs(enemy) &&
                                    (attacker.flying || attacker.noObstacle(it))

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
                !attacker.flying && enemy.weaponAgainst(attacker).minRange() > 0 ||
                        attacker.irridiated ||
                        // Prevent going out of range
                        enemy.hasWeaponAgainst(attacker) && enemy.unitType.canMove() && (enemy.maxRangeVs(attacker) > attacker.maxRangeVs(enemy) && attacker.maxRangeVs(enemy) - attacker.distanceTo(enemy) < 8)

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
            EvadeSuicider(attacker),
            StormDodge(attacker),
            Sequence(
                    Condition { attacker.isOnCoolDown && attacker.canMoveWithoutBreakingAttack },
                    Selector(
                            SpreadOut(attacker),
                            homeInToEnemy,
                            evadeOtherEnemies,
                            kiteEnemy,
                            BetterAltitude(board),
                            airCircle
                    )
            ),
            RetargetWall(board),
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

class EvadeSuicider(private val unit: SUnit) : BehaviorTree() {
    private val board = WithTarget<SUnit>(unit)

    override fun getRoot(): TreeNode = Sequence(
            Condition { !unit.flying },
            SelectTarget(board) {
                units.enemy.nearest(unit.x, unit.y, 128) { it.unitType.suicider }
            },
            Condition {
                board.target!!.distanceTo(board.actor) < 64
            },
            Drag(board)
    )

}

class Stop(private val unit: SUnit) : TreeNode() {
    override fun exec() {
        unit.stop()
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

class Drag(private val board: WithTarget<SUnit>) : TreeNode() {
    override fun exec() {
        val unit = board.actor
        val other = board.target ?: run {
            failed()
            return
        }
        val repelFactor = if (board.target == unit) 0.55 else 0.8
        val avoidAllies = 1.0 - repelFactor

        var force = Potential.repelFrom(unit, other) * repelFactor
        force += Potential.embraceDanger(unit, 64) * 0.5
        if (!board.actor.flying)
            force += Potential.collisionRepulsion(unit) * avoidAllies
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

class RetargetWall(private val board: WithTarget<SUnit>) : TreeNode() {
    override fun exec() {
        val target = board.target ?: run {
            failed()
            return
        }
        val actor = board.actor
        if (!actor.flying && target.distanceTo(actor) < 224) {
            val path = geography.jps.findPath(org.bk.ass.path.Position.of(actor.walkPosition), org.bk.ass.path.Position.of(target.walkPosition), 256f)
            if (path.path.isEmpty()) {
                val hit = geography.walkRay.trace(actor.walkPosition.x, actor.walkPosition.y, target.walkPosition.x, target.walkPosition.y, geography.walkRay.findFirst)
                if (hit != null) {
                    val potentialTarget = units.enemy.nearest(hit.x, hit.y) { !it.flying }
                    if (potentialTarget.enemyUnit)
                        board.target = potentialTarget
                }
            }
        }
        failed()
    }
}

class Intercept(private val board: WithTarget<SUnit>) : TreeNode() {
    override fun exec() {
        val target = board.target ?: run {
            failed()
            return
        }
        val actor = board.actor
        if (target.distanceTo(actor) < 64) {
            var force = Potential.intercept(actor, target)
            if (!actor.flying)
                force += Potential.collisionRepulsion(actor) * 0.2

            Potential.apply(actor, force)
        } else {
            actor.attack(target)
        }
        running()
    }
}

class SpreadOut(private val unit: SUnit) : TreeNode() {
    override fun exec() {
        if (unit.threats.any { it.weaponAgainst(unit).isSplash }) {
            var force = Potential.collisionRepulsion(unit)
            if (force.lengthSquared() > 0) {
                force += Potential.embraceDanger(unit, 32) * 0.3
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
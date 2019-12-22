package org.styx.micro

import bwapi.Bullet
import bwapi.BulletType
import bwapi.UnitType
import org.locationtech.jts.math.Vector2D
import org.styx.*
import org.styx.Styx.game
import org.styx.Styx.geography
import org.styx.Styx.units
import org.styx.action.BasicActions

class Attack(private val attacker: SUnit,
             private val enemy: SUnit) : BehaviorTree("Attack") {
    val kiteEnemy = Seq("Kite enemy",
            Condition {
                attacker.couldTurnAwayAndBackBeforeCooldownEnds(enemy) &&
                        attacker.maxRangeVs(enemy) > enemy.maxRangeVs(attacker) &&
                        enemy.inAttackRange(attacker, 64) &&
                        attacker.inAttackRange(enemy, -16)
            },
            NodeStatus.RUNNING.after {
                val force = Potential.repelFrom(attacker, enemy) +
                        Potential.collisionRepulsion(attacker)
                Potential.apply(attacker, force)
            }
    )

    private val attackEnemy = Seq("Engage",
            Condition {
                val relativeMovement = attacker.velocity.normalize().dot(enemy.velocity.normalize())
                relativeMovement < -0.3 || enemy.distanceTo(attacker) < attacker.maxRangeVs(enemy) + 32 || !attacker.flying && !geography.walkRay.noObstacle(attacker.walkPosition, enemy.walkPosition)
            },
            NodeStatus.RUNNING.after {
                BasicActions.attack(attacker, enemy)
            }
    )
    private val interceptEnemy = Seq("Lead to enemy",
            NodeStatus.RUNNING.after {
                val force = Potential.collisionRepulsion(attacker) * 0.4 +
                        Potential.intercept(attacker, enemy)
                Potential.apply(attacker, force)
            }
    )

    private val evadeOtherEnemies = Seq("Evasive maneuvers",
            Condition { attacker.couldTurnAwayAndBackBeforeCooldownEnds(enemy) },
            {
                val evadeTo = (0..8).asSequence()
                        .map {
                            Vector2D(64.0, 0.0).rotate(it * PI2 / 8)
                                    .toPosition()
                                    .toWalkPosition() + attacker.walkPosition
                        }.filter {
                            enemy.distanceTo(it.toPosition(), attacker.unitType) <= attacker.maxRangeVs(enemy) &&
                                    (attacker.flying || geography.walkRay.noObstacle(attacker.walkPosition, it))

                        }.map {
                            it to attacker.threats.count { t -> t == enemy || t.distanceTo(it.toPosition(), attacker.unitType) <= t.maxRangeVs(attacker) }
                        }.minBy { it.second }
                if (evadeTo != null && evadeTo.second < attacker.threats.size) {
                    BasicActions.move(attacker, evadeTo.first.middlePosition())
                    NodeStatus.RUNNING
                } else
                    NodeStatus.FAILED
            })

    private val hugEnemyWithMinRange = Seq("Hug enemy",
            Condition { enemy.weaponAgainst(attacker).minRange() > 0 || attacker.irridiated },
            NodeStatus.RUNNING.after {
                val force = Potential.intercept(attacker, enemy)
                Potential.apply(attacker, force)
            }
    )

    private val findEnemy = Seq("Locate enemy",
            Condition { !enemy.visible },
            NodeStatus.RUNNING.after {
                BasicActions.move(attacker, enemy.position)
            }
    )
    private val evadeScarab = EvadeScarabs(attacker)
    private val evadeStorms = EvadeStorms(attacker)

    override fun buildRoot(): SimpleNode = Sel("Attack",
            findEnemy,
            evadeScarab,
            evadeStorms,
            Seq("Move while cooldown",
                    Condition { attacker.isOnCoolDown && attacker.canMoveWithoutBreakingAttack },
                    Sel("Sel",
                            hugEnemyWithMinRange,
                            evadeOtherEnemies,
                            kiteEnemy
                    )
            ),
            attackEnemy,
            interceptEnemy
    )
}

class EvadeStorms(private val unit: SUnit) : BehaviorTree("Evade Storm") {
    private var storm: Bullet? = null
    override fun buildRoot(): SimpleNode = Seq("Evade Storm",
            Condition {
                storm = game.bullets.firstOrNull { it.type == BulletType.Psionic_Storm && it.position.getDistance(unit.position) <= 64 }
                storm != null
            },
            NodeStatus.RUNNING.after {
                val force = Potential.repelFrom(unit, storm!!.position) +
                        Potential.collisionRepulsion(unit) * 0.3
                Potential.apply(unit, force)
            }
    )

}

class EvadeScarabs(private val unit: SUnit) : BehaviorTree("Evade Scarabs") {
    private var scarab: SUnit? = null
    override fun buildRoot(): SimpleNode = Seq("Evade Scarabs",
            Condition {
                if (unit.flying)
                    return@Condition false
                scarab = units.enemy.nearest(unit.x, unit.y, 128) { it.unitType == UnitType.Protoss_Scarab }
                scarab != null
            },
            {
                val scarabTarget = scarab!!.target ?: scarab!!
                if (scarabTarget.distanceTo(unit) < 64) {
                    val force = Potential.repelFrom(unit, scarabTarget) +
                            Potential.collisionRepulsion(unit) * 0.3
                    Potential.apply(unit, force)
                    NodeStatus.RUNNING
                } else
                    NodeStatus.FAILED
            }
    )

}

class Stop(private val unit: SUnit) : BehaviorTree("Unit Stop") {
    override fun buildRoot(): SimpleNode = NodeStatus.RUNNING.after { unit.unit.stop() }

}
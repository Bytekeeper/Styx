package org.styx.micro

import bwapi.Bullet
import bwapi.BulletType
import bwapi.UnitType
import org.bk.ass.bt.*
import org.locationtech.jts.math.Vector2D
import org.styx.*
import org.styx.Styx.game
import org.styx.Styx.geography
import org.styx.Styx.units
import org.styx.action.BasicActions
import sun.reflect.generics.tree.Tree

class Attack(private val attacker: SUnit,
             private val enemy: SUnit) : BehaviorTree() {
    val kiteEnemy = Sequence(
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

    private val attackEnemy = Sequence(
            Condition {
                val relativeMovement = attacker.velocity.normalize().dot(enemy.velocity.normalize())
                relativeMovement < -0.3 ||
                        attacker.inAttackRange(enemy) ||
                        enemy.velocity.lengthSquared() < 10 ||
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
    private val interceptEnemy = Sequence(
            NodeStatus.RUNNING.after {
                val force = Potential.collisionRepulsion(attacker) * 0.4 +
                        Potential.intercept(attacker, enemy)
                Potential.apply(attacker, force)
            }
    )

    private val evadeOtherEnemies = Sequence(
            Condition { attacker.couldTurnAwayAndBackBeforeCooldownEnds(enemy) },
            LambdaNode {
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
                    NodeStatus.FAILURE
            })

    private val homeInToEnemy = Sequence(
            Condition {
                enemy.weaponAgainst(attacker).minRange() > 0 ||
                        attacker.irridiated ||
                        enemy.unitType.canMove() && (enemy.maxRangeVs(attacker) > attacker.maxRangeVs(enemy) && attacker.maxRangeVs(enemy) + 8 > attacker.distanceTo(enemy))
            },
            NodeStatus.RUNNING.after {
                val force = Potential.intercept(attacker, enemy)
                Potential.apply(attacker, force)
            }
    )

    private val findEnemy = Sequence(
            Condition { !enemy.visible },
            NodeStatus.RUNNING.after {
                BasicActions.move(attacker, enemy.position)
            }
    )
    private val evadeScarab = EvadeScarabs(attacker)
    private val evadeStorms = StormDodge(attacker)

    override fun getRoot(): TreeNode = Selector(
            findEnemy,
            evadeScarab,
            evadeStorms,
            Sequence(
                    Condition { attacker.isOnCoolDown && attacker.canMoveWithoutBreakingAttack },
                    Selector(
                            homeInToEnemy,
                            evadeOtherEnemies,
                            kiteEnemy
                    )
            ),
            attackEnemy,
            interceptEnemy
    )
}

class StormDodge(private val unit: SUnit) : BehaviorTree() {
    private var storm: Bullet? = null

    override fun getRoot(): TreeNode = Sequence(
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

class EvadeScarabs(private val unit: SUnit) : BehaviorTree() {
    private var scarab: SUnit? = null

    override fun getRoot(): TreeNode = Sequence(
            Condition {
                if (unit.flying)
                    return@Condition false
                scarab = units.enemy.nearest(unit.x, unit.y, 128) { it.unitType == UnitType.Protoss_Scarab }
                scarab != null
            },
            LambdaNode {
                val scarabTarget = scarab!!.target ?: scarab!!
                if (scarabTarget.distanceTo(unit) < 64) {
                    val force = Potential.repelFrom(unit, scarabTarget) +
                            Potential.collisionRepulsion(unit) * 0.3
                    Potential.apply(unit, force)
                    NodeStatus.RUNNING
                } else
                    NodeStatus.FAILURE
            }
    )

}

class Stop(private val unit: SUnit) : TreeNode() {
    override fun exec() {
        unit.stop()
        running()
    }
}
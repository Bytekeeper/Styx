package org.styx.micro

import org.styx.*
import org.styx.Styx.geography
import org.styx.action.BasicActions

class Attack(private val attacker: SUnit,
             private val enemy: SUnit) : BehaviorTree("Attack") {

    override val root: SimpleNode = Sel("Attack",
            Seq("Locate enemy",
                    Condition { !enemy.visible },
                    NodeStatus.RUNNING.after {
                        BasicActions.move(attacker, enemy.position)
                    }
            ),
            Seq("Move while cooldown",
                    Condition { attacker.isOnCoolDown && attacker.canMoveWithoutBreakingAttack },
                    Sel("Sel",
                            Seq("Kite enemy",
                                    Condition {
                                        attacker.maxRangeVs(enemy) > enemy.maxRangeVs(attacker) &&
                                                enemy.inAttackRange(attacker, 64) &&
                                                attacker.inAttackRange(enemy, -16)
                                    },
                                    NodeStatus.RUNNING.after {
                                        val force = Potential.repelFrom(attacker, enemy) +
                                                Potential.collisionRepulsion(attacker)
                                        Potential.apply(attacker, force)
                                    }
                            ),
                            Seq("Hug enemy",
                                    Condition { enemy.weaponAgainst(attacker).minRange() > 0 },
                                    NodeStatus.RUNNING.after {
                                        val force = Potential.intercept(attacker, enemy)
                                        Potential.apply(attacker, force)
                                    }
                            ))
            ),
            Seq("Engage",
                    Condition {
                        val relativeMovement = attacker.velocity.normalize().dot(enemy.velocity.normalize())
                        relativeMovement < -0.3 || enemy.distanceTo(attacker) < attacker.maxRangeVs(enemy) + 32 || !attacker.flying && !geography.walkRay.noObstacle(attacker.walkPosition, enemy.walkPosition)
                    },
                    NodeStatus.RUNNING.after {
                        BasicActions.attack(attacker, enemy)
                    }
            ),
            Seq("Lead to enemy",
                    NodeStatus.RUNNING.after {
                        val force = Potential.collisionRepulsion(attacker) * 0.4 +
                                Potential.intercept(attacker, enemy)
                        Potential.apply(attacker, force)
                    }
            )
    )
}

package org.fttbot.task

import org.fttbot.FTTBot
import org.fttbot.LazyOnFrame
import org.fttbot.ResourcesBoard
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.unit.PlayerUnit
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

enum class TaskStatus {
    RUNNING,
    FAILED,
    DONE;

    inline fun whenFailed(call: () -> Unit): TaskStatus {
        if (this == FAILED) call()
        return this
    }

    inline fun whenRunning(call: () -> Unit): TaskStatus {
        if (this == RUNNING) call()
        return this
    }

    inline fun whenNotDone(call: (TaskStatus) -> Unit): TaskStatus {
        if (this != DONE) call(this)
        return this
    }
}

typealias TaskProvider = () -> List<Task>
typealias UtilityProvider = () -> Double

abstract class Task {
    abstract val utility: Double
    protected val subtasks = mutableListOf<Task>()
    private var lastStatus: TaskStatus = TaskStatus.DONE

    fun process(): TaskStatus {
        val result = processInternal()
        if (result != TaskStatus.RUNNING && result != lastStatus) {
            reset()
        }
        lastStatus = result
        return result
    }

    abstract protected fun processInternal(): TaskStatus
    open fun reset() {
        subtasks.forEach(Task::reset)
    }

    fun repeat(times: Int = -1): Task = Repeating(this, times)
    fun neverFail() = NeverFailing(this)
    override fun toString(): String = this::class.java.name.substringAfterLast('.')

    fun processInSequence(vararg tasks: Task): TaskStatus {
        return tasks.asSequence().map { it.process() }
                .firstOrNull { it in arrayOf(TaskStatus.RUNNING, TaskStatus.FAILED) }
                ?: TaskStatus.DONE
    }

    fun processAll(taskProvider: TaskProvider) = processAll(*taskProvider().toTypedArray())
    fun processAll(vararg tasks: Task): TaskStatus {
        val result = tasks.sortedByDescending { it.utility }.map(Task::process)
        return result.firstOrNull { it == TaskStatus.FAILED }
                ?: result.firstOrNull { it == TaskStatus.RUNNING }
                ?: TaskStatus.DONE
    }

    fun <T : Task> subtask(initializer: () -> T): SubTask<T> = SubTask(initializer)

    fun <T : Task> registerTask(value: T) {
        subtasks += value
    }
}

class SubTask<out T : Task>(val initializer: () -> T) : ReadOnlyProperty<Task, T> {
    private var initialized = false
    private var value: T? = null

    override fun getValue(thisRef: Task, property: KProperty<*>): T {
        if (!initialized) {
            initialized = true
            value = initializer()
            thisRef.registerTask(value as T)
        }
        return value as T

    }
}

class SimpleTask(val task: () -> TaskStatus) : Task() {
    override val utility: Double = 1.0

    override fun processInternal(): TaskStatus = task()
}

abstract class Action : Task() {
    override val utility: Double = 1.0
}

abstract class Decorator(protected val delegate: Task) : Task() {
    override val utility: Double
        get() = delegate.utility

    override fun processInternal(): TaskStatus = delegate.process()

    override fun reset() = delegate.reset()
}

class NeverFailing(task: Task) : Decorator(task) {
    override fun processInternal(): TaskStatus {
        val result = super.processInternal()
        if (result != TaskStatus.RUNNING) return TaskStatus.DONE
        return result
    }

    override fun toString(): String = "NF-$delegate"
}

class Repeating(task: Task, private val times: Int = -1) : Decorator(task) {
    private var count = times

    override fun processInternal(): TaskStatus {
        val result = super.processInternal()
        if (result == TaskStatus.DONE && count != 0) {
            if (count > 0) count--
            super.reset()
            return TaskStatus.RUNNING
        }
        return result
    }

    override fun reset() {
        count = times
        super.reset()
    }

    override fun toString() = "Repeating ($count/$times) $delegate"
}

class UnitLock<T : PlayerUnit>(val invariant: (T) -> Boolean = { true }) {
    private var currentUnit: T? = null
    var reassigned = false
        private set

    fun hasChangedTo(type: UnitType) = currentUnit?.let { FTTBot.game.getUnit(it.id).isA(type) } == true

    fun acquire(search: ((T) -> Boolean) -> T?): T? {
        reassigned = false
        if (currentUnit == null || currentUnit?.exists() == false || !ResourcesBoard.units.contains(currentUnit!!) || !invariant(currentUnit!!)) {
            reassigned = true
            currentUnit = search(invariant)
        }
        if (currentUnit != null) {
            ResourcesBoard.reserveUnit(currentUnit!!)
        }
        return currentUnit
    }

    fun release() {
        currentUnit = null
    }
}

abstract class CompoundTask(val provider: TaskProvider) : Task() {
    val tasks by LazyOnFrame {
        provider().sortedByDescending { it.utility }
    }
    override val utility: Double
        get() = tasks.map { it.utility }.max() ?: 0.0

    override fun reset() {
        tasks.forEach(Task::reset)
    }
}

class MParallelTask(provider: TaskProvider) : CompoundTask(provider) {
    val completedTasks = mutableListOf<Task>()

    override fun processInternal(): TaskStatus {
        val result = (tasks - completedTasks).map { it to it.process() }
        if (result.any { it.second == TaskStatus.FAILED })
            return TaskStatus.FAILED
        completedTasks += result.filter { it.second == TaskStatus.DONE }.map { it.first }
        if (result.none { it.second == TaskStatus.RUNNING })
            return TaskStatus.DONE
        return TaskStatus.RUNNING
    }

    override fun reset() {
        completedTasks.clear()
        super.reset()
    }

}

class ManagedTaskProvider<T>(val itemProvider: () -> List<T>, val taskProvider: (T) -> Task) : TaskProvider {
    val tasks = mutableMapOf<T, Task>()

    override fun invoke(): List<Task> {
        val updatedList = itemProvider()
        tasks.keys.retainAll(updatedList)

        (updatedList - tasks.keys).forEach { tasks[it] = taskProvider(it) }
        return tasks.values.toList()
    }
}
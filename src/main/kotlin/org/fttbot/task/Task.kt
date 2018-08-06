package org.fttbot.task

import org.fttbot.Board.resources
import org.fttbot.LazyOnFrame
import org.openbw.bwapi4j.unit.PlayerUnit

enum class TaskStatus {
    RUNNING,
    FAILED,
    DONE;

    inline fun whenFailed(call: () -> Unit) {
        if (this == FAILED) call()
    }
}

typealias TaskProvider = () -> List<Task>

abstract class Task {
    abstract val utility: Double
    fun process(): TaskStatus {
        val result = processInternal()
        if (result != TaskStatus.RUNNING) {
            reset()
        }
        return result
    }

    abstract protected fun processInternal(): TaskStatus
    open fun reset() {}

    fun repeat(times: Int = -1): Task = Repeating(this, times)
    fun neverFail() = NeverFailing(this)
}

class SimpleTask(val task: () -> TaskStatus) : Task() {
    override val utility: Double = 1.0

    override fun processInternal(): TaskStatus = task()
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

class UnitLock<T : PlayerUnit> {
    var currentUnit: T? = null
        private set

    fun acquire(search: () -> T?): T? {
        if (currentUnit == null || currentUnit?.exists() == false || !resources.units.contains(currentUnit!!)) {
            currentUnit = search()
        }
        if (currentUnit != null) {
            resources.reserveUnit(currentUnit!!)
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

class ParallelTask(provider: TaskProvider) : CompoundTask(provider) {
    override fun processInternal(): TaskStatus {
        val result = tasks.map(Task::process)
        return result.firstOrNull { it == TaskStatus.FAILED }
                ?: result.firstOrNull { it == TaskStatus.RUNNING }
                ?: TaskStatus.DONE
    }
}

class MParallelTask(provider: TaskProvider) : CompoundTask(provider) {
    val completedTasks = mutableListOf<Task>()

    override fun processInternal(): TaskStatus {
        val result = (tasks - completedTasks).map { it to it.process() }
        if (result.any { it.second == TaskStatus.FAILED }) return TaskStatus.FAILED
        completedTasks += result.filter { it.second == TaskStatus.DONE }.map { it.first }
        if (result.none { it.second == TaskStatus.RUNNING }) return TaskStatus.DONE
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
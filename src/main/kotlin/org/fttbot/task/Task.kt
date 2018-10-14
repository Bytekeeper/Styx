package org.fttbot.task

import org.fttbot.LazyOnFrame
import org.fttbot.Locked
import org.fttbot.identity
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

enum class TaskStatus {
    RUNNING,
    FAILED,
    DONE;

    inline fun andWhenFailed(call: () -> Unit): TaskStatus {
        if (this == FAILED) call()
        return this
    }

    inline fun andWhenRunning(call: () -> Unit): TaskStatus {
        if (this == RUNNING) call()
        return this
    }

    inline fun andWhenNotDone(call: (TaskStatus) -> Unit): TaskStatus {
        if (this != DONE) call(this)
        return this
    }
}

typealias TaskProvider = () -> List<Task>
typealias UtilityProvider = () -> Double

abstract class Task {
    abstract val utility: Double
    private val subtasks = mutableListOf<Task>()
    var taskStatus: TaskStatus = TaskStatus.DONE
        private set
    val locks = mutableListOf<Locked<*>>()

    fun process(): TaskStatus {
        locks.forEach(Locked<*>::release)
        val result = processInternal()
        if (result != TaskStatus.RUNNING && result != taskStatus) {
            reset()
        }
        taskStatus = result
        return result
    }

    open fun reset() {
        locks.forEach(Locked<*>::reset)
        subtasks.forEach(Task::reset)
    }

    protected abstract fun processInternal(): TaskStatus

    fun repeat(times: Int = -1): Task = Repeating(this, times)
    fun neverFail() = NeverFailing(this)
    fun nvr() = Repeating(NeverFailing(this))
    override fun toString(): String = this::class.java.name.substringAfterLast('.')

    fun processInSequence(vararg tasks: Task): TaskStatus {
        return tasks.asSequence().sortedByDescending { it.utility }.map { it.process() }
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

abstract class Action(override val utility: Double = 1.0) : Task()

class Condition(private val condition: () -> Boolean) : Action() {
    override fun processInternal(): TaskStatus =
            if (condition()) TaskStatus.DONE else TaskStatus.FAILED
}

class Success(utility: Double = 1.0) : Action(utility) {
    override fun processInternal(): TaskStatus = TaskStatus.DONE
}

class Running(utility: Double = 1.0) : Action(utility) {
    override fun processInternal(): TaskStatus = TaskStatus.RUNNING
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

    override fun toString() = "R ($count/$times) $delegate"
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

open class Sequence(provider: TaskProvider) : CompoundTask(provider) {
    constructor(vararg tasks: Task) : this(tasks.toList()::identity)

    override fun processInternal(): TaskStatus =
            tasks.asSequence().map { it.process() }.firstOrNull { it != TaskStatus.DONE }
                    ?: TaskStatus.DONE
}

open class Fallback(provider: TaskProvider) : CompoundTask(provider) {
    constructor(vararg tasks: Task) : this(tasks.toList()::identity)

    override fun processInternal(): TaskStatus =
            tasks.asSequence().map { it.process() }.firstOrNull { it != TaskStatus.FAILED }
                    ?: TaskStatus.FAILED
}

class ParallelTask(provider: TaskProvider) : CompoundTask(provider) {
    constructor(vararg tasks: Task) : this(tasks.toList()::identity)

    override fun processInternal(): TaskStatus {
        val result = tasks.map { it.process() }
        if (result.any { it == TaskStatus.FAILED })
            return TaskStatus.FAILED
        if (result.none { it == TaskStatus.RUNNING })
            return TaskStatus.DONE
        return TaskStatus.RUNNING
    }
}

class ManagedTaskProvider<T>(val itemProvider: () -> Collection<T>, val taskProvider: (T) -> Task) : TaskProvider {
    val tasks = mutableMapOf<T, Task>()

    override fun invoke(): List<Task> {
        val updatedList = itemProvider()
        tasks.keys.retainAll(updatedList)

        (updatedList - tasks.keys).forEach { tasks[it] = taskProvider(it) }
        return tasks.values.toList()
    }
}
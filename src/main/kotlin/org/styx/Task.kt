package org.styx

import org.styx.tasks.Attacking
import org.styx.tasks.Gathering
import org.styx.tasks.Scouting


enum class FlowStatus {
    INITIAL, RUNNING, DONE, FAILED;

    fun then(callback: () -> FlowStatus): FlowStatus {
        if (this == DONE) return callback()
        return this
    }
}

interface Flow {
    fun execute(): FlowStatus
}

typealias TaskProvider = () -> List<Task>

fun Iterable<TaskProvider>.flatten(): TaskProvider = { flatMap { it() } }

abstract class Task : Flow {
    abstract val priority: Int
    var status: FlowStatus = FlowStatus.INITIAL
        private set

    final override fun execute(): FlowStatus {
        status = performExecute()
        return status
    }

    abstract protected fun performExecute(): FlowStatus

    open fun reset() {
        status = FlowStatus.INITIAL
    }

    fun singleton(): TaskProvider = { listOf(this@Task) }
}

object Running : Task() {
    override val priority: Int
        get() = 0

    override fun performExecute(): FlowStatus = FlowStatus.RUNNING
}

abstract class CompoundTask(private val taskProvider: TaskProvider) : Task() {
    protected val tasks: List<Task> by LazyOnFrame { taskProvider().sortedByDescending { it.priority } }

    override val priority: Int = tasks.map { it.priority }.max() ?: -1

    override fun reset() {
        super.reset()
        tasks.forEach(Task::reset)
    }
}

class MParallel(taskProvider: TaskProvider) : CompoundTask(taskProvider) {
    private val done = mutableSetOf<Task>()

    constructor(vararg tasks: Task) : this({ tasks.toList() })

    override fun reset() {
        super.reset()
        done.clear()
    }

    override fun performExecute(): FlowStatus =
            tasks.asSequence().dropWhile {
                done.contains(it)
            }.map { it to it.execute() }
                    .dropWhile { (t, r) ->
                        if (r == FlowStatus.DONE) {
                            done += t
                            true
                        } else r == FlowStatus.FAILED
                    }.firstOrNull()?.second ?: FlowStatus.DONE
}

class Parallel(taskProvider: TaskProvider) : CompoundTask(taskProvider) {
    override fun performExecute(): FlowStatus {
        val result = tasks.map { it to it.execute() }.filter { it.second != FlowStatus.DONE }
        if (result.any { it.second == FlowStatus.FAILED }) return FlowStatus.FAILED
        if (result.any { it.second == FlowStatus.RUNNING }) return FlowStatus.RUNNING
        return FlowStatus.DONE
    }
}

class MappedTasks<T>(private val source: () -> Iterable<T>, private val map: (T) -> Task) : TaskProvider {

    private var tasks = mapOf<T, Task>()

    override fun invoke(): List<Task> {
        tasks = tasks.filter { (_, task) -> task.status == FlowStatus.RUNNING } +
                source().filter { !tasks.containsKey(it) }
                        .map { it to map(it) }
        return tasks.values.toList()
    }

}

class Repeat(private val task: Task) : Task() {
    override val priority: Int
        get() = task.priority

    override fun performExecute(): FlowStatus {
        val flowStatus = task.execute()
        if (flowStatus == FlowStatus.FAILED) return FlowStatus.FAILED
        if (flowStatus == FlowStatus.RUNNING) return FlowStatus.RUNNING
        task.reset()
        return FlowStatus.RUNNING
    }
}

class If(private val condition: () -> Boolean, private val task: Task, private val otherwise: Task = Running) : Task() {
    override val priority: Int
        get() = if (condition()) task.priority else otherwise.priority

    override fun reset() {
        super.reset()
        task.reset()
        otherwise.reset()
    }

    override fun performExecute(): FlowStatus =
            if (condition()) {
                if (otherwise.status != FlowStatus.INITIAL) otherwise.reset()
                task.execute()
            } else {
                if (task.status != FlowStatus.INITIAL) task.reset()
                otherwise.execute()
            }

}

class TaskProviders {
    val taskProviders = listOf(
            Gathering(),
            Attacking().singleton(),
            Scouting().singleton(),
            { listOf(test) }
    )
}
package org.fttbot

import org.apache.logging.log4j.LogManager
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.unit.PlayerUnit

interface Result<T>
class RFail<T>(val reason: String) : Result<T> {
    fun <O> map() = RFail<O>(reason)
}

class RBusy<T> : Result<T> {
    fun <O> map() = RBusy<O>()
}

class RResult<T>(val result: T) : Result<T>

interface Flow<T> {
    fun get(): Result<T>

    fun <O> _if(cond: (T) -> Result<Boolean>, then: Flow<T>.() -> Flow<O>, _else: Flow<T>.() -> Flow<O> = {
        object : Flow<O> {
            override fun get(): Result<O> = RBusy()
        }
    }): Flow<O> = object : Flow<O> {
        var input: T? = null

        private val thenFlow: Flow<O>
        private val elseFlow: Flow<O>

        init {
            val inputFlow = flow { input!! }
            thenFlow = then(inputFlow)
            elseFlow = _else(inputFlow)

        }

        override fun get(): Result<O> {
            val inflow = this@Flow.get()
            if (inflow is RFail) {
                return inflow.map()
            }
            if (inflow is RBusy) {
                return RBusy()
            }
            input = (inflow as RResult).result
            val conditionResult = cond(input!!)
            if (conditionResult is RBusy) {
                return RBusy()
            }
            if (conditionResult is RFail) {
                return conditionResult.map()
            }
            conditionResult as RResult
            return if (conditionResult.result) thenFlow.get() else elseFlow.get()
        }
    }

    fun <O> then(map: (T) -> Result<O>): Flow<O> = withResult("then") { map(it) }

    fun thenTake(consumer: (T) -> Unit): Flow<T> = object : Flow<T> {
        override fun get(): Result<T> = then { consumer(it); RResult(it) }.get()
    }

    fun <O> or(vararg map: (T) -> Result<O>): Flow<O> = object : Flow<O> {
        override fun get(): Result<O> {
            val result = this@Flow.get()
            if (result is RFail) {
                return result.map()
            }
            if (result is RBusy) {
                return RBusy()
            }
            result as RResult<T>
            map.forEach {
                val tmp = it(result.result)
                if (tmp is RBusy) {
                    return RBusy()
                }
                if (tmp is RResult) {
                    return tmp
                }
            }
            return RFail("OR exhausted")
        }

        override fun toString(): String = "${this@Flow} or"
    }

    fun <O> once(map: Flow<T>.() -> Flow<O>): Flow<O> = object : Flow<O> {
        var result: Result<O> = RBusy()
        val downFlow = map(this@Flow)

        override fun get(): Result<O> {
            if (result !is RBusy) {
                val inflow = this@Flow.get()
                if (inflow is RFail)
                    return inflow.map()
                return result
            }
            result = downFlow.get()
            return result
        }

        override fun toString(): String = "${this@Flow} once($downFlow)"
    }

    fun reserveUnit(unitExtractor: (T) -> PlayerUnit): Flow<T> = withResult("reserve unit") {
        val toReserve = unitExtractor(it)
        if (Board.resources.units.contains(toReserve)) {
            Board.resources.reserveUnit(toReserve)
            RResult(it)
        } else
            RFail<T>("Unit $toReserve is not available")
    }

    fun reserveResources(minerals: (T) -> Int, gas: (T) -> Int = { 0 }, supply: (T) -> Int = { 0 }): Flow<T> = withResult("reserving resources") {
        val actualSupply = supply(it)
        Board.resources.reserve(minerals(it), gas(it), actualSupply)
        return@withResult if (Board.resources.enoughMineralsAndGas() && (actualSupply == 0 || Board.resources.supply >= 0))
            RResult(it)
        else
            RBusy<T>()
    }

    fun reserveResources(unitTypeExtractor: (T) -> UnitType): Flow<T> {
        val min = { it: T -> unitTypeExtractor(it).mineralPrice() }
        val gas = { it: T -> unitTypeExtractor(it).gasPrice() }
        val supply = { it: T -> unitTypeExtractor(it).supplyRequired() }
        return reserveResources(min, gas, supply)
    }

    fun <O> withResult(title: String, handler: (T) -> Result<O>): Flow<O> = object : Flow<O> {
        override fun get(): Result<O> {
            val result = this@Flow.get()
            if (result is RFail) {
                return result.map()
            }
            if (result is RBusy) {
                return RBusy()
            }
            result as RResult
            return handler(result.result)
        }

        override fun toString(): String = "${this@Flow} ${title}"
    }

    fun waitFor(cond: (T) -> Boolean): Flow<T> = withResult("waiting for condition") {
        val result = cond(it)
        if (result) {
            RResult(it)
        } else {
            RBusy<T>()
        }
    }

    fun sleep(frames: Int): Flow<T> = object : Flow<T> {
        private var counter = frames

        override fun get(): Result<T> {
            val inflow = this@Flow.get()
            if (inflow is RFail)
                return inflow.map()
            if (inflow is RBusy)
                return inflow.map()
            if (counter == 0)
                return inflow
            if (counter > 0)
                counter--
            return RBusy()
        }

    }

    fun passOn(title: String, handler: (T) -> Unit): Flow<T> = withResult(title) { handler(it); RResult(it) }

    fun <O> retry(times: Int = 5, downFlowProvider: Flow<T>.() -> Flow<O>) : Flow<O> = object : Flow<O> {
        val log = LogManager.getLogger()
        var downFlow : Flow<O> = downFlowProvider(this@Flow)
        var remaining = times
        override fun get(): Result<O> {
            val res = downFlow.get()
            if (res is RBusy)
                return res.map()
            if (res is RResult)
                return res
            res as RFail
            if (remaining <= 0)
                return res.map()
            log.debug("Failed: ${res.reason}, retrying $remaining times")
            remaining--
            downFlow = downFlowProvider(this@Flow)
            return RBusy()
        }

    }

    companion object {
        fun <O> flow(supplier: () -> O): Flow<O> = object : Flow<O> {
            override fun get(): Result<O> = RResult(supplier())
        }

        fun <O> once(flow: Flow<O>): Flow<O> = object : Flow<O> {
            var result: Result<O> = RBusy()

            override fun get(): Result<O> {
                if (result is RBusy) {
                    result = flow.get()
                }
                return result
            }

        }
    }
}

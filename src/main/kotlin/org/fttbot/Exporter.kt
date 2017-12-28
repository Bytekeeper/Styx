package org.fttbot

import bwapi.*
import java.io.PrintWriter
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

object Exporter {
    fun export() {
        internalExport(PrintWriter("out/FUnitTypes.kt"), UnitType::class.java)
        internalExport(PrintWriter("out/WeaponTypes.kt"), WeaponType::class.java)
        internalExport(PrintWriter("out/UpgradeTypes.kt"), UpgradeType::class.java)
        internalExport(PrintWriter("out/TechTypes.kt"), TechType::class.java)
        internalExport(PrintWriter("out/Orders.kt"), Order::class.java)
        internalExport(PrintWriter("out/BulletTyes.kt"), BulletType::class.java)
        println("done")
    }

    fun <T> internalExport(out: PrintWriter, what: Class<T>) {
        val typeToExport = what
        val declaredConstants = typeToExport.declaredFields.filter { it.type == typeToExport }.map { it.name to it.get(typeToExport) as T }
        val gettersOfProperties = typeToExport.declaredMethods
                .filter { it.parameterCount == 0 && it.returnType != Void.TYPE }
                .filter {
                    when (it.name) {
                        "toString" -> false
                        else -> true
                    }
                }

        val pars = gettersOfProperties.map {
            var varName = toVarName(it.returnType, it.name)
            var kType = toKotlinType(it.returnType, it.genericReturnType)
            if (willBeWrapped(it.returnType)) {
                "private val ${varName} : Lazy<$kType>"
            } else {
                "val ${varName} : $kType"
            }
        }.joinToString()
        val myType = "F${typeToExport.simpleName}"
        out.println("package org.fttbot.import")
        out.println("import bwapi.*")
        val sourceType = what.simpleName
        out.print("class $myType(val source : $sourceType, val name : String")
        if (pars.length > 0) {
            out.println(", ${pars}) {")
        } else {
            out.println(") {")
        }
        out.println("   init {")
        out.println("      $myType.instances[source] = this")
        out.println("   }")
        gettersOfProperties.filter { willBeWrapped(it.returnType) }
                .map { "   val ${it.name} : ${toKotlinType(it.returnType, it.genericReturnType)} by ${toVarName(it.returnType, it.name)}" }
                .forEach(out::println)
        out.println("   override fun toString() = name")
        out.println()
        out.println("   companion object {")
        out.println("      internal val instances = HashMap<$sourceType, $myType>()")
        out.println("      fun of(src : $sourceType) : $myType = instances[src]!!")
        declaredConstants.map { (name, ut) ->
            "$name : $myType = $myType(" +
                    (listOf("source = $sourceType.$name, name = \"${name}\"") + gettersOfProperties.map {
                        val value = it.invoke(ut)
                        val valueAsString = when (value) {
                            is Boolean, is String, is Int, is Double -> value
                            is ExplosionType, is DamageType, is UnitSizeType, is Race -> "${it.returnType.simpleName}.${value}"
                            is TilePosition -> "TilePosition(${value.x}, ${value.y})"
                            is Order, is UnitType, is TechType, is UpgradeType, is WeaponType -> "lazy(LazyThreadSafetyMode.NONE) {F${it.returnType.simpleName}.${value}}"
                            is Pair<*, *> -> "lazy(LazyThreadSafetyMode.NONE) {FUnitType.${value.first} to ${value.second}}"
                            is List<*> -> when {
                                value.isEmpty() -> {
                                    val actualType = (it.genericReturnType as ParameterizedType).actualTypeArguments[0]
                                    "lazy(LazyThreadSafetyMode.NONE) {emptyList<${toKotlinType(actualType as Class<*>, actualType)}>()}"
                                }
                                else -> "lazy(LazyThreadSafetyMode.NONE) {listOf(" + value.joinToString { "F${it!!::class.java.simpleName}.${it}" } + ")}"
                            }
                            is Map<*, *> -> when {
                                value.isEmpty() -> {
                                    val actualTypes = (it.genericReturnType as ParameterizedType).actualTypeArguments
                                    "lazy(LazyThreadSafetyMode.NONE) {emptyMap<${toKotlinType(actualTypes[0] as Class<*>, actualTypes[1])}, ${toKotlinType(actualTypes[1] as Class<*>, actualTypes[1])}>()}"
                                }
                                else -> "lazy(LazyThreadSafetyMode.NONE)  {mapOf(" + value.map { (k, v) -> "F${k!!::class.java.simpleName}.${k} to $v" }.joinToString() + ")}"
                            }
                            else -> "ERR: $value ${it.returnType}"
                        }
                        val varName = toVarName(it.returnType, it.name)

                        "${varName} = ${valueAsString}"
                    }).joinToString() + ")"
        }.forEach { out.println("      val $it") }
        out.println("   }")
        out.println("}")
        out.close()
    }

    private fun toKotlinType(type: Class<*>, genericType: Type): String {
        return when (type) {
            Boolean::class.java -> "Boolean"
            Pair::class.java -> "kotlin.Pair<FUnitType, Int>"
            Integer::class.java, Int::class.java -> "Int"
            Double::class.java -> "Double"
            List::class.java -> {
                val actualType = (genericType as ParameterizedType).actualTypeArguments[0]
                "List<" + toKotlinType(actualType as Class<*>, actualType) + ">"
            }
            Map::class.java -> {
                val actualTypes = (genericType as ParameterizedType).actualTypeArguments
                "Map<" + toKotlinType(actualTypes[0] as Class<*>, actualTypes[0]) + ", " + toKotlinType(actualTypes[1] as Class<*>, actualTypes[1]) + ">"
            }
            else -> if (willBeWrapped(type)) "F${type.simpleName}" else type.simpleName
        }
    }

    private fun willBeWrapped(type: Class<*>) = when (type) {
        ExplosionType::class.java, DamageType::class.java, UnitSizeType::class.java, Race::class.java, TilePosition::class.java -> false
        else -> !type.isPrimitive
    }

    private fun toVarName(type: Class<*>, name: String) = if (willBeWrapped(type)) "l_$name" else name
}
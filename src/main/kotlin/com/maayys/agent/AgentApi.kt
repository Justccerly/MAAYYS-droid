package com.maayys.agent

import kotlin.math.roundToInt
import kotlin.random.Random

data class Rect(val x:Int,val y:Int,val width:Int,val height:Int)
interface Controller { fun click(x:Int,y:Int):Boolean; fun swipe(sx:Int,sy:Int,ex:Int,ey:Int,durationMs:Long):Boolean }
interface CustomAction { fun run(arg:String, box:Rect?, controller:Controller?):Boolean }
class ActionRegistry { private val actions=mutableMapOf<String,CustomAction>(); fun register(name:String, action:CustomAction){actions[name]=action}; fun run(name:String,arg:String,box:Rect?,controller:Controller?)=actions[name]?.run(arg,box,controller) ?: false }

class RandomTouchAction : CustomAction {
    private val gaussian = java.util.Random()
    override fun run(arg: String, box: Rect?, controller: Controller?): Boolean {
        if (box == null || controller == null || box.width <= 0 || box.height <= 0) return false
        val right = box.x.toLong() + box.width - 1
        val bottom = box.y.toLong() + box.height - 1
        if (box.x < 0 || box.y < 0 || right > Int.MAX_VALUE || bottom > Int.MAX_VALUE) return false
        val x = (box.x + box.width / 2.0 + gaussian.nextGaussian() * box.width / 6)
            .coerceIn(box.x.toDouble(), right.toDouble()).roundToInt()
        val y = (box.y + box.height / 2.0 + gaussian.nextGaussian() * box.height / 6)
            .coerceIn(box.y.toDouble(), bottom.toDouble()).roundToInt()
        return controller.click(x, y)
    }
}
class RandomSwipeAction : CustomAction {
    private val gson = com.google.gson.GsonBuilder().setStrictness(com.google.gson.Strictness.STRICT).create()
    override fun run(arg: String, box: Rect?, controller: Controller?): Boolean {
        if (controller == null || Thread.currentThread().isInterrupted) return false
        return try {
            val element = gson.fromJson(arg, com.google.gson.JsonElement::class.java) ?: return false
            if (!element.isJsonObject) return false
            val params = element.asJsonObject
            fun integer(value: com.google.gson.JsonElement): Int {
                require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber)
                return value.asString.toIntOrNull() ?: error("Expected integer")
            }
            fun roi(name: String): IntArray {
                val value = params.get(name) ?: error("Missing ROI")
                require(value.isJsonArray && value.asJsonArray.size() == 4)
                val a = value.asJsonArray.map { integer(it) }.toIntArray()
                require(a[0] >= 0 && a[1] >= 0 && a[2] > 0 && a[3] > 0)
                require(a[0].toLong() + a[2] - 1 <= Int.MAX_VALUE && a[1].toLong() + a[3] - 1 <= Int.MAX_VALUE)
                return a
            }
            val a = roi("start_roi"); val b = roi("end_roi")
            val delay = params.get("delay")?.takeUnless { it.isJsonNull }?.let { integer(it) } ?: 0
            // Android safety bound, shared with RootController. Upstream has no upper bound.
            val duration = if (delay == 0) 200 else delay
            require(duration in 1..60_000)
            controller.swipe(a[0] + Random.nextInt(a[2]), a[1] + Random.nextInt(a[3]),
                b[0] + Random.nextInt(b[2]), b[1] + Random.nextInt(b[3]), duration.toLong())
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        } catch (_: Exception) { false }
    }
}
class RandomWaitAction(
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
    private val random: () -> Double = { Random.nextDouble() }
) : CustomAction {
    private val gson = com.google.gson.GsonBuilder().setStrictness(com.google.gson.Strictness.STRICT).create()
    override fun run(arg: String, box: Rect?, controller: Controller?): Boolean {
        if (Thread.currentThread().isInterrupted) return false
        if (arg.isEmpty()) return true
        return try {
            val element = gson.fromJson(arg, com.google.gson.JsonElement::class.java) ?: return false
            if (!element.isJsonObject) return false
            val params = element.asJsonObject
            fun number(name: String): Double {
                val value = params.get(name) ?: return 0.0
                if (!value.isJsonPrimitive) return 0.0
                val primitive = value.asJsonPrimitive
                return if (primitive.isNumber || primitive.isString) primitive.asString.toDoubleOrNull() ?: 0.0 else 0.0
            }
            val a = number("min"); val b = number("max")
            if (!a.isFinite() || !b.isFinite() || a < 0 || b < 0) return false
            val low = minOf(a, b); val high = maxOf(a, b)
            if (high == 0.0) return true
            if (high >= Long.MAX_VALUE.toDouble() / 1000.0) return false
            val seconds = if (low == high) low else {
                val fraction = random()
                require(fraction >= 0 && fraction < 1)
                low + fraction * (high - low)
            }
            sleep((seconds * 1000).toLong())
            true
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        } catch (_: Exception) { false }
    }
}
fun defaultRegistry()=ActionRegistry().apply { register("RandomTouch",RandomTouchAction()); register("RandomSwipe",RandomSwipeAction()); register("RandomWait",RandomWaitAction()) }

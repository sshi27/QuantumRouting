package utils

import java.math.BigInteger
import kotlin.math.absoluteValue

fun dynSearch(xMin: Double, xMax: Double, yTarget: Double, f: (Double) -> Double, fIsIncreasing: Boolean, precision: Double): Double {
  var x = (xMin + xMax) / 2
  var step = x
  
  for (i in (0..100)) {
    require({ i < 100 })
    step /= 2
    
    val y = f(x)
    if (Math.abs(y - yTarget) < Math.abs(precision)) break
    
    if ((y > yTarget) xor fIsIncreasing) {
      x += step
    } else {
      x -= step
    }
  }
  return x
}

fun Double.format(digits: Int, padTo: Int = 0) = java.lang.String.format("%${padTo + digits + 1}.${digits}f", this)

fun Int.padTo(digits: Int) = String.format("%0${digits}d", this)

infix fun Int.pow(n: Int) = (1..n).fold(1) { acc, _ ->
  this * acc
}

fun Int.digits(base: Int, padTo: Int = 0): List<Int> {
  val digits = mutableListOf<Int>()
  
  var tmp = this.absoluteValue
  while (tmp != 0 || padTo != 0 && digits.size < padTo) {
    digits.add(tmp % base)
    tmp /= base
  }
  
  return digits
}

fun Int.factorial(until: Int = 1) = (until + 1..this).fold(BigInteger.ONE, { acc, i -> acc * i.toBigInteger() })


fun C(totalNum: Int, targetNum: Int) = totalNum.factorial(totalNum - targetNum) / targetNum.factorial()
fun P(totalNum: Int, targetNum: Int) = totalNum.factorial(totalNum - targetNum)


fun Int.factorial_(until: Int = 1) = (until + 1..this).fold(1.0, { acc, i -> acc * i })

fun C_(totalNum: Int, targetNum: Int) = totalNum.factorial_(totalNum - targetNum) / targetNum.factorial_()
fun P_(totalNum: Int, targetNum: Int) = totalNum.factorial_(totalNum - targetNum)


val requireLevel = 0
fun require(b: () -> Boolean, msg: String = "failed requirement", level: Int = 0) {
  if (level >= requireLevel && !b()) {
    System.err.println(msg)
  }
}

fun <T> HashSet<T>.pop(): T {
  val e = first()
  remove(e)
  return e
}

fun <T> MutableList<T>.removeUntil(f: (T) -> Boolean): T? {
  while (isNotEmpty()) {
    val e = removeAt(0)
    if (f(e)) return e
  }
  return null
}

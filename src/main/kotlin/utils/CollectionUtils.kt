package utils

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import nl.komponents.kovenant.combine.Tuple2
import nl.komponents.kovenant.combine.Tuple3
import nl.komponents.kovenant.combine.Tuple4
import nl.komponents.kovenant.combine.Tuple5
import nl.komponents.kovenant.combine.Tuple6
import nl.komponents.kovenant.combine.Tuple7
import java.util.*
import java.util.Collections.swap

fun <A, B> List<A>.pmap(f: suspend (A) -> B): List<B> = runBlocking {
  map { async { f(it) } }.map { it.await() }
}

fun <T> List<T>.toPair() = this[0] to this[1]
fun <A, B, C> Pair<A, B>.contains(e: C) = first == e || second == e
fun <T> Pair<T, T>.otherThan(element: T) = if (element == this.first) second else if (element == this.second) first else throw Exception("Neither")

operator fun <T, U> List<T>.times(another: List<U>) = this.flatMap { a -> another.map { b -> a to b } }

infix fun <A, B, C> Pair<A, B>.also(third: C): Triple<A, B, C> = Triple(first, second, third)

data class Tuple1<V1>(val first: V1)

object Tuple {
  operator fun <A> invoke(_1: A) = Tuple1(_1)
  operator fun <A, B> invoke(_1: A, _2: B) = Tuple2(_1, _2)
  operator fun <A, B, C> invoke(_1: A, _2: B, _3: C) = Tuple3(_1, _2, _3)
  operator fun <A, B, C, D> invoke(_1: A, _2: B, _3: C, _4: D) = Tuple4(_1, _2, _3, _4)
  operator fun <A, B, C, D, E> invoke(_1: A, _2: B, _3: C, _4: D, _5: E) = Tuple5(_1, _2, _3, _4, _5)
  operator fun <A, B, C, D, E, F> invoke(_1: A, _2: B, _3: C, _4: D, _5: E, _6: F) = Tuple6(_1, _2, _3, _4, _5, _6)
  operator fun <A, B, C, D, E, F, G> invoke(_1: A, _2: B, _3: C, _4: D, _5: E, _6: F, _7: G) = Tuple7(_1, _2, _3, _4, _5, _6, _7)
}

fun isRange(l: List<Int>): Boolean {
  if (l.isEmpty()) return true
  
  var f = l.first()
  l.drop(1).forEach {
    if (it != ++f) return false
  }
  
  return true
}

class ReducibleLazyEvaluation<K, V>(val initializer: (K) -> V, val pre: (K) -> K = { it }, val post: (K, V) -> V = { _, it -> it }) : HashMap<K, V>() {
  override fun get(key: K): V {
    var res = super.get(pre(key))
    
    if (res != null) return res
    
    res = initializer(key)
    put(key, res)
    return post(key, res)
  }
}

fun <T> Collection<T>.combinations(n: Int = 2): List<List<T>> {
  if (this.size < n) return listOf()
  
  val list = this.toMutableList()
  
  val result: MutableList<List<T>> = mutableListOf()
  
  fun work(step: Int, selected: Stack<T>) {
    if (selected.size == n) {
      result.add(selected.toList())
      return
    }
    
    selected.push(list[step])
    work(step + 1, selected)
    selected.pop()
    
    if (selected.size + this.size - (step + 1) >= n)
      work(step + 1, selected)
  }
  
  work(0, Stack())
  return result
}

fun <T> List<T>.permutations(): List<List<T>> {
  val result: MutableList<List<T>> = mutableListOf()
  val mutableList = this.toMutableList()
  
  fun work(step: Int) {
    if (step == mutableList.size - 1) {
      result.add(mutableList.toList())
      return
    }
    
    (step until mutableList.size).forEach { i ->
      swap(mutableList, step, i)
      work(step + 1)
      swap(mutableList, step, i)
    }
  }
  
  work(0)
  return result
}

fun <T> List<T>.arrangements(n: Int = 2): List<List<T>> {
  return combinations(n).flatMap { it.permutations() }
}

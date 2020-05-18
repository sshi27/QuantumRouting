package utils

import java.util.*

class DisjointSet(n: Int) {
  val parentOf = (0 until n).toMutableList()
  
  fun getRepresentative(i: Int): Int {
    val stack = Stack<Int>()
    var tmp = i
    while (parentOf[tmp] != tmp) {
      stack.push(tmp)
      tmp = parentOf[tmp]
    }
    
    stack.forEach { parentOf[it] = tmp }
    
    return tmp
  }
  
  fun merge(i: Int, j: Int) {
    if (!sameDivision(i, j))
      parentOf[getRepresentative(j)] = getRepresentative(i)
  }
  
  fun sameDivision(i: Int, j: Int) = getRepresentative(i) == getRepresentative(j)
}

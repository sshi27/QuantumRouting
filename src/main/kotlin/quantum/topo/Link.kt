package quantum.topo

import quantum.randGen
import utils.format
import utils.padTo
import utils.require
import kotlin.math.pow

class Link(val topo: Topo, val n1: Node, val n2: Node, val l: Double, var entangled: Boolean = false, var s1: Boolean = false, var s2: Boolean = false, val id: Int = cnt++) {
  companion object {
    var cnt = 0
  }
  
  fun theOtherEndOf(n: Node) = if (n1 == n) n2 else if (n2 == n) n1 else throw Exception("No such node")
  fun contains(n: Node) = n1 == n || n2 == n
  fun swappedAt(n: Node) = (n1 == n && s1 || n2 == n && s2)
  fun swappedAtTheOtherEndOf(n: Node) = (n1 == n && s2 || n2 == n && s1)
  fun swapped() = s1 || s2
  fun notSwapped() = !swapped()
  
  override fun hashCode() = id
  override fun equals(other: Any?) = other != null && other is Link && other.id == id
  
  var assigned = false
    set(value) {
      if (field == value) return
      
      if (value) {
        n1.remainingQubits--
        n2.remainingQubits--
      } else {
        n1.remainingQubits++
        n2.remainingQubits++
      }
      field = value
      require({ n1.remainingQubits >= 0 && n1.remainingQubits <= n1.nQubits })
      require({ n2.remainingQubits >= 0 && n2.remainingQubits <= n2.nQubits })
    }
  
  fun assignQubits() {
    this.assigned = true
  }
  
  val p = Math.E.pow(-topo.alpha * l)
  
  fun tryEntanglement(): Boolean {
    val b = assigned && p >= randGen.nextDouble()
    entangled = b
    return b
  }
  
  fun clearEntanglement() {
    assigned = false
    entangled = false
  }
  
  override fun toString(): String {
    return "L#${id.padTo(topo.linkDigits)} ${if (entangled) "✓" else "✗"} ${if (assigned) "[" else "("}${n1.id.padTo(topo.nodeDigits)},${n2.id.padTo(topo.nodeDigits)}${if (assigned) "]" else ")"} ${l.format(2, topo.distanceDigits)}km"
  }
  
  fun assignable() = !assigned && n1.remainingQubits > 0 && n2.remainingQubits > 0
}

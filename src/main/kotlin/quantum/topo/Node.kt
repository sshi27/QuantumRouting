package quantum.topo

import quantum.randGen
import utils.format
import utils.padTo
import utils.require

class Node(val topo: Topo, val id: Int, val loc: DoubleArray, val nQubits: Int) {
  val links: MutableList<Link> = mutableListOf()
  val internalLinks: MutableList<Pair<Link, Link>> = mutableListOf()
  
  val neighbors: Set<Node> by lazy { links.map { it.theOtherEndOf(this) }.toSet() }
  
  fun attemptSwapping(l1: Link, l2: Link): Boolean {
    if (l1.n1 == this) {
      require({ !l1.s1 })
      l1.s1 = true
    } else {
      require({ !l1.s2 })
      l1.s2 = true
    }
    if (l2.n1 == this) {
      require({ !l2.s1 })
      l2.s1 = true
    } else {
      require({ !l2.s2 })
      l2.s2 = true
    }
    
    val b = randGen.nextDouble() <= topo.q
    if (b) {
      internalLinks.add(l1 to l2)
    }
    return b
  }
  
  var remainingQubits: Int = nQubits
  
  
  override fun toString(): String {
    return "N#${id.padTo(topo.nodeDigits)}"
  }
  
  fun toFullString(): String {
    return "N#${id.padTo(topo.nodeDigits)} [$remainingQubits/$nQubits] @ ${loc.map { it.format(2, topo.distanceDigits) }.joinToString(", ", "(", ")")}"
  }
}

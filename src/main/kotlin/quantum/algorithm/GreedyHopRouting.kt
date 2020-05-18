package quantum.algorithm

import quantum.topo.Edge
import quantum.topo.Node
import quantum.topo.Topo
import quantum.topo.edges
import utils.also

class GreedyHopRouting(topo: Topo) : Algorithm(topo) {
  override val name: String = "Greedy_H"
  
  val pathsSortedDynamically: MutableList<PickedPath> = mutableListOf()
  
  override fun prepare() {
    utils.require({ topo.isClean() })
    //print("""pre-calculation: """.trimIndent())
    val start = System.nanoTime()
    // calculate all paths for all pairs. Avoid qubit and link contentions by always satisfying shorter paths
    
    //println("${(System.nanoTime() - start) / 1E6} ms")
  }
  
  override fun P2() {
    pathsSortedDynamically.clear()
    
    //print("""assign qubits to links: """.trimIndent())
    val start = System.nanoTime()
    
    while (srcDstPairs.any fxx@{ (src, dst) ->
        val p: MutableList<Node> = mutableListOf(src)
        
        while (true) {
          val last = p.last()
          if (last == dst) break
          val next = last.neighbors.filter { neighbor ->
            (neighbor.remainingQubits > 2 || neighbor == dst && neighbor.remainingQubits > 1) &&
              last.links.count { !it.assigned && it.contains(neighbor) } > 0
          }.minBy { topo.hopsAway[Edge(it, dst)] }
          
          if (next == null || next in p) break
          p += next
        }
        
        if (p.last() != dst)
          return@fxx false
        
        val width = topo.widthPhase2(p)
        if (width == 0)
          return@fxx false
        
        pathsSortedDynamically.add(0.0 to width also p)

//        println("Select path: $p")
        
        (1..width).forEach {
          p.edges().forEach { (n1, n2) ->
            n1.links.first { it.contains(n2) && !it.assigned }.assignQubits()
          }
        }
        
        true
      });
    
    //println("${(System.nanoTime() - start) / 1E6} ms")
  }
  
  override fun P4() {
    //print("""connect links internally: """.trimIndent())
    val start = System.nanoTime()
    
    pathsSortedDynamically.forEach { (_, width, p) ->
      val oldNumOfPairs = topo.getEstablishedEntanglements(p.first(), p.last()).size
      
      p.dropLast(2).zip(p.drop(1).dropLast(1)).zip(p.drop(2)).forEach { (n12, next) ->
        val (prev, n) = n12
        
        val prevLinks = n.links.filter { it.entangled && (it.n1 == prev && !it.s2 || it.n2 == prev && !it.s1) }.sortedBy { it.id }.take(width)
        val nextLinks = n.links.filter { it.entangled && (it.n1 == next && !it.s2 || it.n2 == next && !it.s1) }.sortedBy { it.id }.take(width)
        
        prevLinks.zip(nextLinks).forEach { (l1, l2) ->
          n.attemptSwapping(l1, l2)
        }
      }
      
      val succ = topo.getEstablishedEntanglements(p.first(), p.last()).size - oldNumOfPairs
      logWriter.appendln(""" ${p.map { it.id }}, $width $succ""")
    }
  }
}

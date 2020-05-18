package quantum.algorithm

import quantum.topo.*
import utils.*
import java.lang.Math.E
import java.lang.Math.ceil
import java.util.*
import kotlin.math.log2
import kotlin.math.pow

abstract class OfflinePathBasedAlgorithm(topo: Topo, val allowRecoveryPaths: Boolean = true) : Algorithm(topo) {
  val pathsSortedStatically: ReducibleLazyEvaluation<Pair<Node, Node>, List<PickedPath>> = ReducibleLazyEvaluation(
    { (src, dst) ->
      val q = PriorityQueue<Pair<Double, Path>>(Comparator { (l1, _), (l2, _) ->
        l1.compareTo(l2)
      })
      
      val edges = topo.links.groupBy { it.n1 to it.n2 }.map { it.value[0].n1 to it.value[0].n2 }.toHashSet()
      val visited: HashMap<HashSet<Edge>, Pair<Double, Path>> = hashMapOf()
      
      val p = topo.shortestPath(edges, src, dst, fStateMetric)
      visited[edges.toHashSet()] = p
      
      if (p.second.isNotEmpty())
        if (p !in q) q.add(p)
      
      fun work() {
        visited.toList().forEach { (edges, p) ->
          if (p.second.isEmpty()) return
          val relatedEdges = p.second.edges().toSet()
          
          relatedEdges.forEach {
            edges.remove(it)
            if (edges !in visited) {
              val p = topo.shortestPath(edges, src, dst, fStateMetric)
              visited[edges.toHashSet()] = p
              
              if (p.second.isNotEmpty() && p !in q) q.add(p)
            }
            edges.add(it)
          }
        }
      }
      
      var tries = 0
      while (q.size < 50 && tries++ < 100) {
//        assert(q.distinct().size == q.size)
        work()
      }
      
      val tmp = mutableListOf<PickedPath>()
      while (q.isNotEmpty()) {
        val p = q.poll()
        val width = topo.widthPhase2(p.second)
        tmp.add(p.first to width also p.second)
      }
      
      tmp
    },
    { (src, dst) ->
      if (src.id > dst.id) Pair(dst, src)
      else Pair(src, dst)
    },
    { (src, dst), path ->
      if (src.id > dst.id) path.reversed()
      else path
    }
  )
  
  val pathsSortedDynamically: MutableList<PickedPath> = mutableListOf()
  val extraPaths: MutableList<PickedPath> = mutableListOf()

//  val pathsSortedByHops = ReducibleLazyEvaluation<Pair<Node, Node>, List<PickedPath>>({ pathsSortedStatically[it].sortedBy { it.third.size } })
  
  open val fStateMetric: ReducibleLazyEvaluation<Edge, Double> = ReducibleLazyEvaluation({ +(it.n1.loc - it.n2.loc) + topo.internalLength })
  
  override fun prepare() {
    require({ topo.isClean() })
    //print("""pre-calculation: """.trimIndent())
    val start = System.nanoTime()
    // calculate all paths for all pairs. Avoid qubit and link contentions by always satisfying shorter paths
    
    //println("${(System.nanoTime() - start) / 1E6} ms")
  }
  
  abstract val dynamicComparator: Comparator<PickedPath>
  
  override fun P2() {
    pathsSortedDynamically.clear()
    extraPaths.clear()
    
    //print("""assign qubits to links: """.trimIndent())
    val start = System.nanoTime()
    
    val allPaths = srcDstPairs.flatMap { pathsSortedStatically[it] }
    
    val q = PriorityQueue<PickedPath>(dynamicComparator)
    q.addAll(allPaths)
    
    var cnt = 0
    while (q.isNotEmpty() && cnt++ < 100 * srcDstPairs.size) {
      val p = q.poll()
      val width = topo.widthPhase2(p.third)
      
      if (width == 0) {
        q.add(p)
        break
      } // all rest paths are not fully available now.
      
      if (width < p.second) {
        q.add(p.first to width also p.third)
        continue
      }
      
      pathsSortedDynamically.add(p)

//      println("Select path: $p")
      
      (1..width).forEach {
        p.third.edges().forEach { (n1, n2) ->
          n1.links.first { (it.n1 == n2 || it.n2 == n2) && !it.assigned }.assignQubits()
        }
      }
    }
    
    P2Online(q)
    
    if (allowRecoveryPaths) {
      P2Recovery(q)
    }
    
    //println("${(System.nanoTime() - start) / 1E6} ms")
  }
  
  private fun P2Recovery(q: PriorityQueue<PickedPath>) {
    val secondaryPaths = pathsSortedDynamically + q
    secondaryPaths.forEach { p ->
      var sum = 0
      p.third.edges().forEach { (n1, n2) ->
        val available = n1.links.filter { it.contains(n2) && !it.assigned }
        sum += available.size
        available.forEach { if (n1.remainingQubits > 0 && n2.remainingQubits > 0) it.assignQubits() }
      }
      
      if (sum > 0)
        logWriter.appendln(""" ${p.third.map { it.id }}, $sum // recovery""")
    }
  }
  
  open fun P2Online(q: PriorityQueue<PickedPath>) {
    // sort paths by the number of common nodes
    
    while (srcDstPairs.any fxx@{ (src, dst) ->
        val maxM = Math.min(src.remainingQubits, dst.remainingQubits)
        
        var picked = false
        
        for (width in (maxM downTo 1)) {
          val failNodes = (topo.nodes - src - dst).filter { it.remainingQubits < 2 * width }.toHashSet()
          
          val edges = topo.links.filter {
            !it.assigned && it.n1 !in failNodes && it.n2 !in failNodes
          }.groupBy { it.n1 to it.n2 }.filter { it.value.size >= width }.map { it.key }.toHashSet()
          
          val neighborsOf = ReducibleLazyEvaluation<Node, MutableList<Node>>({ mutableListOf() })
          
          edges.forEach {
            neighborsOf[it.n1].add(it.n2)
            neighborsOf[it.n2].add(it.n1)
          }
          
          if (neighborsOf[src].isEmpty() || neighborsOf[dst].isEmpty())
            continue
          
          val p = topo.shortestPath(edges, src, dst, fStateMetric)
          if (p.second.isEmpty()) continue
          
          extraPaths.add(p.first to width also p.second)

//          println("Online Selected path: $p")
          
          (1..width).forEach {
            p.second.edges().forEach { (n1, n2) ->
              n1.links.first { (it.n1 == n2 || it.n2 == n2) && !it.assigned }.assignQubits()
            }
          }
          
          picked = true
          break
        }
        
        picked
      });
  }
  
  override fun P4() {
    //print("""connect links internally: """.trimIndent())
    val start = System.nanoTime()
    (pathsSortedDynamically + extraPaths).forEach { (_, width, p) ->
      val oldNumOfPairs = topo.getEstablishedEntanglements(p.first(), p.last()).size
      
      p.dropLast(2).zip(p.drop(1).dropLast(1)).zip(p.drop(2)).forEach { (n12, next) ->
        val (prev, n) = n12
        
        val prevLinks = n.links.filter { it.entangled && !it.swappedAt(n) && it.contains(prev) }.sortedBy { it.id }.take(width)
        val nextLinks = n.links.filter { it.entangled && !it.swappedAt(n) && it.contains(next) }.sortedBy { it.id }.take(width)
        
        prevLinks.zip(nextLinks).forEach { (l1, l2) ->
          n.attemptSwapping(l1, l2)
        }
      }
      
      val succ = topo.getEstablishedEntanglements(p.first(), p.last()).size - oldNumOfPairs
      logWriter.appendln(""" ${p.map { it.id }}, $width $succ // online""")
    }
    
    P4Adaptive()
    
    //println("${(System.nanoTime() - start) / 1E6} ms")
    logWriter.appendln()
  }
  
  val segmentsToTry = ReducibleLazyEvaluation<Int, List<Pair<Int, Int>>>({ length ->
    (ceil(log2(length.toDouble())).toInt() downTo 0).map { 2.pow(it) }.flatMap { step ->
      val points = (0 until length step step).toMutableList()
      if (points.last() != length - 1) points.add(length - 1)
      points.dropLast(1).zip(points.drop(1))
    }.distinct()
  })
  
  fun P4Adaptive() {
    val visited = HashSet<Link>(topo.links.filter { it.swapped() })
    
    pathsSortedDynamically.forEach { (_, width, p) ->
      val oldNumOfPairs = topo.getEstablishedEntanglements(p.first(), p.last()).size
      val nodes = p.toTypedArray()
      val pendingInbound = p.map { it to hashSetOf<Link>() }.toMap()
      val pendingOutbound = p.map { it to hashSetOf<Link>() }.toMap()
      
      (1..width).forEach {
        val segmentsToTry = LinkedList<Pair<Int, Int>>()
        segmentsToTry.add(0 to p.size - 1)
        
        while (segmentsToTry.isNotEmpty()) {
          val (si, di) = segmentsToTry.pollFirst()!!
          val (src, dst) = nodes[si] to nodes[di]
          
          val links = topo.kHopNeighborLinks(src).intersect(topo.kHopNeighborLinks(dst)).filter { it.assigned }
          val edges = links.groupBy { it.n1 to it.n2 }.map { it.key }
          
          val rp = topo.shortestPath(edges, src, dst, fStateMetric)
          
          if (rp.second.isEmpty()) { // nodes only know qubit assignment. cannot decide on the link state
            val mi = (si + di) / 2
            if (mi != si) segmentsToTry.add(si to mi)
            if (mi != di) segmentsToTry.add(mi to di)
          } else {
            // as long as a path is assigned, it will stop iteration. This path may be broken.
            while (true) { // we don't want this loop. But because nodes do not know states on other recovery paths, they should assume they are the hero
              val elinks = links.filter { it.entangled && it !in visited }
              val edges = elinks.map { it.n1 to it.n2 }.toSet()
              
              val (_, rp) = topo.shortestPath(edges, src, dst, fStateMetric)
              if (rp.isEmpty()) break
              
              rp.dropLast(2).zip(rp.drop(1).dropLast(1)).zip(rp.drop(2)).forEach fxx@{ (n12, next) ->
                val (prev, n) = n12
                
                val prevLink = n.links.filter {
                  it.entangled && it.contains(prev) && !it.swappedAt(n) &&
                    !(pendingInbound[prev]?.contains(it) ?: false)
                }.minBy { it.id }
                
                val nextLink = n.links.filter {
                  it.entangled && it.contains(next) && !it.swappedAt(n) &&
                    !(pendingOutbound[next]?.contains(it) ?: false)
                }.minBy { it.id }
                
                if (prevLink == null || nextLink == null) return@fxx
                visited.add(prevLink); visited.add(nextLink)
                
                if (prev == rp.first() && !prevLink.swappedAt(prev)) {
                  val pendIn = pendingInbound[prev]!!.sortedBy { it.id }.toMutableList()
                  val pin = pendIn.removeUntil { !it.swappedAt(prev) }
                  
                  if (pin != null) {
                    prev.attemptSwapping(pin, prevLink)
                  } else {
                    pendingOutbound[prev]!!.add(prevLink)
                  }
                }
                
                if (next == rp.last() && !nextLink.swappedAt(next)) {
                  val pendOut = pendingOutbound[next]!!.sortedBy { it.id }.toMutableList()
                  val pout = pendOut.removeUntil { !it.swappedAt(next) }
                  
                  if (pout != null) {
                    next.attemptSwapping(pout, nextLink)
                  } else {
                    pendingInbound[next]!!.add(nextLink)
                  }
                }
                n.attemptSwapping(prevLink, nextLink)
              }
              
              if (rp.size == 2) { // previous loop is not visited
                val (prev, next) = rp
                
                val pendIn = pendingInbound[prev]!!.sortedBy { it.id }.toMutableList()
                val pendOut = pendingOutbound[next]!!.sortedBy { it.id }.toMutableList()
                
                val link = prev.links.filter { it.entangled && it.contains(next) && !(it.s1 && it.s2) && it !in pendIn && it !in pendOut && it !in visited }.minBy { it.id }
                
                if (link != null) {
                  visited.add(link)
                  
                  val pin = pendIn.removeUntil { !it.swappedAt(prev) }
                  
                  if (pin != null && !link.swappedAt(prev)) {
                    prev.attemptSwapping(pin, link)
                  } else {
                    pendingOutbound[prev]!!.add(link)
                  }
                  
                  val pout = pendOut.removeUntil { !it.swappedAt(next) }
                  if (pout != null && !link.swappedAt(next)) {
                    next.attemptSwapping(pout, link)
                  } else {
                    pendingInbound[next]!!.add(link)
                  }
                }
              }
            }
          }
        }
      }
      
      val succ = topo.getEstablishedEntanglements(p.first(), p.last()).size - oldNumOfPairs
      logWriter.appendln(""" ${p.map { it.id }}, $width $succ // offline""")
    }
  }
}

class SumDist(topo: Topo, allowRecoveryPaths: Boolean = true) : OfflinePathBasedAlgorithm(topo, allowRecoveryPaths) {
  override val name: String = "SumDist" + if (allowRecoveryPaths) "" else "-R"
  override val dynamicComparator = Comparator<PickedPath> { (m1), (m2) ->
    m1.compareTo(m2)
  }
}

class CreationRate(topo: Topo, allowRecoveryPaths: Boolean = true) : OfflinePathBasedAlgorithm(topo, allowRecoveryPaths) {
  override val name: String = "CR" + if (allowRecoveryPaths) "" else "-R"
  override val fStateMetric: ReducibleLazyEvaluation<Edge, Double> = ReducibleLazyEvaluation({ E.pow(topo.alpha * +(it.n1.loc - it.n2.loc)) })
  override val dynamicComparator = Comparator<PickedPath> { (m1), (m2) ->
    m1.compareTo(m2)
  }
}

open class MultiMetric(topo: Topo, allowRecoveryPaths: Boolean = true) : OfflinePathBasedAlgorithm(topo, allowRecoveryPaths) {
  override val name: String = "MultiMetric" + if (allowRecoveryPaths) "" else "-R"
  override val dynamicComparator = Comparator<PickedPath> { m1, m2 ->
    val (v1, v2) = listOf(m1, m2).map { log2(it.first) - it.second + if (it.second == 1) 10000 else 0 }
    v1.compareTo(v2)
  }
}

class BotCap(topo: Topo, allowRecoveryPaths: Boolean = true) : OfflinePathBasedAlgorithm(topo, allowRecoveryPaths) {
  override val name: String = "BotCap" + if (allowRecoveryPaths) "" else "-R"
  override val dynamicComparator = Comparator<PickedPath> { m1, m2 ->
    m2.second.compareTo(m1.second) * 10000 + m1.first.compareTo(m2.first)
  }
}

class GlobalLinkState(topo: Topo, allowRecoveryPaths: Boolean = true) : MultiMetric(topo, allowRecoveryPaths) {
  override val name: String = "MultiMetric_G"
  override fun P4() {
    //print("""connect links internally: """.trimIndent())
    val start = System.nanoTime()
    
    val freeValidLinks = topo.links.filter { it.entangled }.toHashSet()
    
    while (true) {
      val edges = freeValidLinks.groupBy { it.n1 to it.n2 }.map { it.value[0].n1 to it.value[0].n2 }.toHashSet()
      
      val taken = srcDstPairs.map { (src, dst) ->
        topo.shortestPath(edges, src, dst, ReducibleLazyEvaluation({ 1.0 }))
      }.filter { it.second.isNotEmpty() }.minBy { it.first }
      
      if (taken == null) break
      
      val path = taken.second
      val width = topo.widthPhase4(path)
      
      if (path.size == 2) {
        path[0].links.filter { it.entangled && !it.swapped() && (it.contains(path[1])) }.forEach { freeValidLinks.remove(it) }
      }
      
      path.dropLast(2).zip(path.drop(1).dropLast(1)).zip(path.drop(2)).forEach { (n12, next) ->
        val (prev, n) = n12
        
        val prevLinks = n.links.filter { it.entangled && (it.n1 == prev && !it.s2 || it.n2 == prev && !it.s1) }.sortedBy { it.id }.take(width)
        val nextLinks = n.links.filter { it.entangled && (it.n1 == next && !it.s2 || it.n2 == next && !it.s1) }.sortedBy { it.id }.take(width)
        
        prevLinks.zip(nextLinks).forEach { (l1, l2) ->
          n.attemptSwapping(l1, l2)
          freeValidLinks.remove(l1)
          freeValidLinks.remove(l2)
        }
      }
    }
    
    //println("${(System.nanoTime() - start) / 1E6} ms")
  }
}

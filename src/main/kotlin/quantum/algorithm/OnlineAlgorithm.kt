package quantum.algorithm

import quantum.topo.*
import utils.ReducibleLazyEvaluation
import utils.also
import utils.pmap
import utils.require
import java.util.*
import kotlin.collections.HashMap

open class OnlineAlgorithm(topo: Topo, val allowRecoveryPaths: Boolean = true) : Algorithm(topo) {
  override val name: String = "Online" + if (allowRecoveryPaths) "" else "-R"
  
  override fun prepare() {
  }
  
  val majorPaths = mutableListOf<PickedPath>()
  val recoveryPaths = HashMap<PickedPath, LinkedList<PickedPath>>()
  
  override fun P2() {
    require({ topo.isClean() })
    majorPaths.clear()
    recoveryPaths.clear()
    pathToRecoveryPaths.clear()
    
    while (true) {
      val candidates = calCandidates(srcDstPairs)
      
      val pick = candidates.maxBy { it.first }
      if (pick != null && pick.first > 0.0) {
        pickAndAssignPath(pick)
      } else {
        break
      }
    }
    
    if (allowRecoveryPaths)
      P2Extra()
  }
  
  fun P2Extra() {
    majorPaths.forEach { majorPath ->
      val (_, _, p) = majorPath
      (1..topo.k).forEach { l ->
        (0..p.size - l - 1).forEach { i ->
          val (src, dst) = p[i] to p[i + l]
          
          val candidates = calCandidates(listOf(Pair(src, dst)))
          val pick = candidates.maxBy { it.first }
          if (pick != null && pick.first > 0.0) {
            pickAndAssignPath(pick, majorPath)
          }
        }
      }
    }
  }
  
  // if majorPath is null: *pick* is a major path
  // else: *pick* is a recovery path
  fun pickAndAssignPath(pick: PickedPath, majorPath: PickedPath? = null) {
    if (majorPath != null)
      recoveryPaths[majorPath]!!.add(pick)
    else {
      majorPaths.add(pick)
      recoveryPaths[pick] = LinkedList<PickedPath>()
    }
    
    val width = pick.second
    val toAdd = Triple(mutableListOf<LinkBundle>(), width, mutableMapOf<Edge, List<Pair<Connection, Int>>>())
    
    pick.third.edges().forEach { (n1, n2) ->
      val links = n1.links.filter { !it.assigned && it.contains(n2) }.sortedBy { it.id }.subList(0, width)
      require({ links.size == width })
      toAdd.first.add(links)
      links.forEach {
        it.assignQubits()
        it.tryEntanglement() // just for display
      }
    }
  }
  
  fun calCandidates(ops: List<Pair<Node, Node>>): List<PickedPath> {
    return ops.pmap fxx@{ (src, dst) ->
      val maxM = Math.min(src.remainingQubits, dst.remainingQubits)
      if (maxM == 0) return@fxx null
      
      var candidate: PickedPath? = null
      
      for (w in (maxM downTo 1)) {
        val failNodes = (topo.nodes - src - dst).filter { it.remainingQubits < 2 * w }.toHashSet()
        
        val edges = topo.links.filter {
          !it.assigned && it.n1 !in failNodes && it.n2 !in failNodes
        }.groupBy { it.n1 to it.n2 }.filter { it.value.size >= w }.map { it.key }.toHashSet()
        
        val neighborsOf = ReducibleLazyEvaluation<Node, MutableList<Node>>({ mutableListOf() })
        
        edges.forEach {
          neighborsOf[it.n1].add(it.n2)
          neighborsOf[it.n2].add(it.n1)
        }
        
        if (neighborsOf[src].isEmpty() || neighborsOf[dst].isEmpty())
          continue
        
        val prevFromSrc: HashMap<Node, Node> = hashMapOf()
        
        fun getPathFromSrc(n: Node): MutableList<Node> {
          val path = LinkedList<Node>()
          
          var cur = n
          while (cur != topo.sentinal) {
            path.addFirst(cur)
            cur = prevFromSrc[cur]!!
          }
          return path.toMutableList()
        }
        
        val E = topo.nodes.map { Double.NEGATIVE_INFINITY to DoubleArray(w + 1, { 0.0 }) }.toTypedArray()
        val q = PriorityQueue<Edge>(Comparator { (o1, _), (o2, _) ->
          -E[o1.id].first.compareTo(E[o2.id].first)
        })
        
        E[src.id] = Double.POSITIVE_INFINITY to DoubleArray(w + 1, { 0.0 })
        q.offer(src to topo.sentinal)
        
        while (q.isNotEmpty()) {
          val (u, prev) = q.poll()  // invariant: top of q reveals the node with highest e under m
          if (u in prevFromSrc) continue  // skip same node suboptimal paths
          prevFromSrc[u] = prev // record
          
          if (u == dst) {
            candidate = E[u.id].first to w also getPathFromSrc(dst)
            break
          }
          
          neighborsOf[u].forEach { neighbor ->
            val tmp = E[u.id].second.clone()
            val e = topo.e(getPathFromSrc(u) + neighbor, w, tmp)
            val newE = e to tmp
            val oldE = E[neighbor.id]
            
            if (oldE.first < newE.first) {
              E[neighbor.id] = newE
              q.offer(neighbor to u)
            }
          }
        }
        
        if (candidate != null) break
      }
      
      candidate
    }.filter { it != null } as List<PickedPath>
  }
  
  data class RecoveryPath(val path: Path, val width: Int, var taken: Int = 0, var available: Int = 0)
  
  val pathToRecoveryPaths = ReducibleLazyEvaluation<PickedPath, MutableList<RecoveryPath>>({ mutableListOf() })
  
  override fun P4() {
    majorPaths.forEach { pathWithWidth ->
      val (_, width, majorPath) = pathWithWidth
      val oldNumOfPairs = topo.getEstablishedEntanglements(majorPath.first(), majorPath.last()).size  // just for logging
      
      val recoveryPaths = this.recoveryPaths.get(pathWithWidth)!!.sortedBy { it.third.size * 10000 + majorPath.indexOf(it.third.first()) }
      
      recoveryPaths.forEach { (_, w, p) ->
        val available = p.edges().map { (n1, n2) -> n1.links.count { it.contains(n2) && it.entangled } }.min()!!
        pathToRecoveryPaths[pathWithWidth].add(RecoveryPath(p, w, 0, available))
      }
      
      val edges = (0..majorPath.size - 2).zip(1..majorPath.size - 1)
      val rpToWidth = recoveryPaths.map { it.third to it.second }.toMap().toMutableMap()
      
      for (i in (1..width)) {   // for w-width major path, treat it as w different paths, and repair separately
        // find all broken edges on the major path
        val brokenEdges = LinkedList(edges.filter { (i1, i2) ->
          val (n1, n2) = majorPath[i1] to majorPath[i2]
          n1.links.any { it.contains(n2) && it.assigned && it.notSwapped() && !it.entangled }
        })
        
        val edgeToRps = brokenEdges.map { it to mutableListOf<Path>() }.toMap()
        val rpToEdges = recoveryPaths.map { it.third to mutableListOf<Pair<Int, Int>>() }.toMap()
        
        recoveryPaths.forEach { (_, _, rp) ->  // rp is calculated from P2
          val (s1, s2) = majorPath.indexOf(rp.first()) to majorPath.indexOf(rp.last())
          
          (s1..s2 - 1).zip(s1 + 1..s2).filter { it in brokenEdges }.forEach {
            rpToEdges[rp]!!.add(it)
            edgeToRps[it]!!.add(rp)
          }
        }
        
        var realPickedRps = HashSet<Path>()
        var realRepairedEdges = hashSetOf<Pair<Int, Int>>()
        
        // try to cover the broken edges
        for (brokenEdge in brokenEdges) {
          if (realRepairedEdges.contains(brokenEdge)) continue  // repaired edges will never change this state, for low time complexity.
          var repaired = false
          var next = 0
          
          tryRp@ for (rp in edgeToRps[brokenEdge]!!.filter { rpToWidth[it]!! > 0 && it !in realPickedRps }
            .sortedBy { majorPath.indexOf(it.first()) * 10000 + majorPath.indexOf(it.last()) }) {  // available rp, sorted by positions of their first switch node
            if (majorPath.indexOf(rp.first()) < next) continue
            next = majorPath.indexOf(rp.last())
            
            val pickedRps = realPickedRps.toHashSet()
            val repairedEdges = realRepairedEdges.toHashSet()
            
            val otherCoveredEdges = rpToEdges[rp]!!.toHashSet() - brokenEdge
            
            for (edge in otherCoveredEdges) { // delete covered rps, or abort
              val prevRp = edgeToRps[edge]!!.intersect(pickedRps).minusElement(rp).firstOrNull()  // the previous rp is covered
              
              if (prevRp == null) {
                repairedEdges.add(edge)
              } else {
                continue@tryRp  // the rps overlap. taking time to search recursively. just abort
              }
            }
            
            repaired = true
            repairedEdges.add(brokenEdge)
            pickedRps.add(rp)
            
            (realPickedRps - pickedRps).forEach { rpToWidth[it] = rpToWidth[it]!! + 1 }   // release the previous rps
            (pickedRps - realPickedRps).forEach { rpToWidth[it] = rpToWidth[it]!! - 1 }
            
            realPickedRps = pickedRps
            realRepairedEdges = repairedEdges
            break  // one rp is sufficient. after all, we cannot swap one link to two
          }
          
          if (!repaired) {  // this major path cannot be repaired
            break  //
          }
        }
        
        val p = realPickedRps.fold(majorPath) { acc, rp ->
          val pathData = pathToRecoveryPaths[pathWithWidth].first { it.path == rp }
          pathData.taken++
          
          val toAdd = rp.edges()
          val toDelete = acc.dropWhile { it != rp.first() }.dropLastWhile { it != rp.last() }.edges()
          
          val edgesOfNewPathAndCycles = acc.edges().toSet() - toDelete + toAdd
          
          val p = topo.shortestPath(edgesOfNewPathAndCycles, acc.first(), acc.last(), ReducibleLazyEvaluation({ 1.0 })).second
          p
        }
        
        p.dropLast(2).zip(p.drop(1).dropLast(1)).zip(p.drop(2)).forEach { (n12, next) ->
          val (prev, n) = n12
          
          val prevLinks = n.links.filter { it.entangled && !it.swappedAt(n) && it.contains(prev) }.sortedBy { it.id }.take(1)
          val nextLinks = n.links.filter { it.entangled && !it.swappedAt(n) && it.contains(next) }.sortedBy { it.id }.take(1)
          
          prevLinks.zip(nextLinks).forEach { (l1, l2) ->
            n.attemptSwapping(l1, l2)
          }
        }
      }
      
      val succ = topo.getEstablishedEntanglements(majorPath.first(), majorPath.last()).size - oldNumOfPairs
      logWriter.appendln(""" ${majorPath.map { it.id }}, $width $succ""")
      pathToRecoveryPaths[pathWithWidth].forEach {
        logWriter.appendln("""  ${it.path.map { it.id }}, $width ${it.available} ${it.taken}""")
      }
    }
    
    logWriter.appendln()
  }
}

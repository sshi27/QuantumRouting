package quantum.topo

import quantum.edgeLen
import quantum.maxSearchHops
import quantum.randGen
import utils.*
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.*
import kotlin.collections.HashSet
import kotlin.math.*

infix fun Node.to(n2: Node) = Edge(this, n2)

data class Edge(val n1: Node, val n2: Node) {
  constructor(p: Pair<Node, Node>) : this(p.first, p.second)
  
  fun toList() = listOf(n1, n2)
  
  fun otherThan(n: Node) = if (n == n1) n2 else if (n == n2) n1 else throw Exception("Neither")
  fun contains(n: Node) = n1 == n || n2 == n
  override fun hashCode() = n1.id xor n2.id
  override fun equals(other: Any?) = other is Edge && (other.n1 == n1 && other.n2 == n2 || other.n1 == n2 && other.n2 == n1)
}

typealias Path = List<Node>
typealias LinkBundle = List<Link>
typealias Connection = List<LinkBundle>

fun Path.edges(): List<Edge> = this.dropLast(1).zip(this.drop(1)).map { Edge(it) }

fun Path.applyCycle(cycle: Path): Path {
  // one and only one path shares one or more path segments with this cycle
  // and this cycle has no less width than the path
  val _cycle = cycle + cycle[0]
  val cycleEdges = _cycle.edges().toSet()
  val commonEdges = cycleEdges.intersect(edges())
  
  val (toDelete, toAdd) = commonEdges to (cycleEdges - commonEdges)
  
  val edgesOfNewPathAndCycles = edges().toSet() - toDelete + toAdd
  
  val p = cycle.first().topo.shortestPath(edgesOfNewPathAndCycles, first(), last(), ReducibleLazyEvaluation({ 1.0 })).second
  return p
}

class Topo(input: String) {
  constructor(input: Topo) : this(input.toString())
  
  val n: Int
    get() = nodes.size
  var alpha: Double
  var q: Double
  var k: Int
  
  val nodes: MutableList<Node> = mutableListOf()
  var links: MutableList<Link> = mutableListOf()
  
  val sentinal: Node
  
  val nodeDigits: Int
  val linkDigits: Int
  val distanceDigits: Int
  
  val internalLength: Double
  
  init {
    val lines = LinkedList(input.lines().map { it.replace(Regex("""\s*//.*$"""), "") }.filter { it.isNotBlank() })
    val n = lines.pollFirst()!!.toInt()
    sentinal = Node(this, -1, doubleArrayOf(-1.0, -1.0, -1.0), Int.MAX_VALUE)
    
    alpha = lines.pollFirst()!!.toDouble()
    q = lines.pollFirst()!!.toDouble()
    k = lines.pollFirst()!!.toInt()
    internalLength = Math.log(1 / q) / alpha
    
    (0 until n).forEach { i ->
      val line = lines.pollFirst()!!
      val tmp = line.split(" ").map { it.toDouble() }
      val node = Node(this, i, doubleArrayOf(tmp[1], tmp[2]), tmp[0].toInt())
      nodes.add(node)
    }
    
    nodeDigits = Math.ceil(Math.log10(nodes.size.toDouble())).roundToInt()
    
    while (lines.isNotEmpty()) {
      val linkLine = lines.pollFirst()!!
      val tmp = linkLine.split(" ").map { it.toInt() }
      val (n1, n2) = tmp.subList(0, 2).map { nodes[it] }.sortedBy { it.id }
      val nm = tmp[2]
      
      require({ n1.id < n2.id })
      
      (1..nm).forEach {
        val link = Link(this, n1, n2, +(n1.loc - n2.loc))
        links.add(link)
        n1.links.add(link)
        n2.links.add(link)
      }
    }
    
    linkDigits = Math.ceil(Math.log10(links.size.toDouble())).roundToInt()
    
    distanceDigits = Math.ceil(Math.log10((links.map { it.l } + nodes.flatMap { it.loc.map { it.absoluteValue } }).max()!!)).roundToInt()
  }
  
  override fun toString(): String {
    return toConfig()
  }
  
  fun toFullString() = """
$n
$alpha
$q
$k
${nodes.map { "${it.remainingQubits}/${it.nQubits} ${it.loc.joinToString(" ")}" }.joinToString("\n")}
${links.groupBy { it.n1 to it.n2 }.map { "${it.key.n1.id} ${it.key.n2.id} ${it.value.count { it.assigned }}/${it.value.size}" }.joinToString("\n")}
  """.trimIndent()
  
  fun toConfig() = """
$n
$alpha
$q
$k
${nodes.map { "${it.nQubits} ${it.loc.joinToString(" ")}" }.joinToString("\n")}
${links.groupBy { it.n1 to it.n2 }.map { "${it.key.n1.id} ${it.key.n2.id} ${it.value.size}" }.joinToString("\n")}
""".trim().trimIndent()
  
  fun getStatistics(): String {
    val numLinks = nodes.map { it.links.size }.sorted()
    val numNeighbors = nodes.map { n -> n.links.groupBy { (it.n1 to it.n2).otherThan(n) }.size }.sorted()
    val avgLinks = numLinks.sum().toDouble() / n
    val avgNeighbors = numNeighbors.sum().toDouble() / n
    
    val linkLengths = links.map { +(it.n1.loc - it.n2.loc) }.sorted()
    val avgLinkLength = linkLengths.sumByDouble { it } / links.size
    
    val linkSuccPossibilities = links.map { Math.E.pow(-alpha * +(it.n1.loc - it.n2.loc)) }.sorted()
    val avglinkSuccP = linkSuccPossibilities.sum() / links.size
    
    val numQubits = nodes.map { it.nQubits }.sorted()
    val avgQubits = numQubits.sum().toDouble() / n
    
    return """
      Topology:
      ${n} nodes, ${links.size} links         alpha: ${alpha}  q: ${q}
      #links     per node                (Max, Avg, Min): ${numLinks.last()}   	${avgLinks.format(2)}	${numLinks.first()}
      #qubits    per node                (Max, Avg, Min): ${numQubits.last()}   	${avgQubits.format(2)}	${numQubits.first()}
      #neighbors per node                (Max, Avg, Min): ${numNeighbors.last()}   	${avgNeighbors.format(2)}	${numNeighbors.first()}
      length of links       (km)         (Max, Avg, Min): ${linkLengths.last().format(2)}	${avgLinkLength.format(2)}	${linkLengths.first().format(2)}
      P(entanglement succeed for a link) (Max, Avg, Min): ${linkSuccPossibilities.last().format(2)}	${avglinkSuccP.format(2)}	${linkSuccPossibilities.first().format(2)}
      
      """.trimIndent()
  }
  
  fun clearEntanglements() {
    links.forEach {
      it.clearEntanglement()
      it.s1 = false
      it.s2 = false
    }
    
    nodes.forEach { it.internalLinks.clear() }
    
    require({ nodes.all { it.nQubits == it.remainingQubits } })
  }
  
  fun clearOnlyPhase4() {
    links.forEach {
      it.s1 = false
      it.s2 = false
    }
    
    nodes.forEach { it.internalLinks.clear() }
  }
  
  fun isClean() = links.all { !it.entangled && !it.assigned && it.notSwapped() }
    && nodes.all { it.internalLinks.isEmpty() } && nodes.all { it.nQubits == it.remainingQubits }
  
  fun kHopNeighbors(root: Node, k: Int = this.k): HashSet<Node> {
    if (k > this.k) return nodes.toHashSet() // as long as the graph is connected
    val registered = nodes.map { false }.toTypedArray()
    
    val stack = Stack<Node>()
    stack.push(root)
    registered[root.id] = true
    
    fun work() {
      val current = stack.peek()
      
      if (stack.size <= k + 1) {
        val unregisteredNeighbors = current.links.map { (it.n1 to it.n2).otherThan(current) }.filter { !registered[it.id] }.toSet()
        
        unregisteredNeighbors.forEach {
          registered[it.id] = true
          stack.push(it)
          work()
          stack.pop()
        }
      }
    }
    
    work()
    return registered.mapIndexed { index, b -> if (b) nodes[index] else sentinal }.filter { it != sentinal }.toHashSet()
  }
  
  fun kHopNeighborLinks(root: Node, k: Int = this.k): HashSet<Link> {
    val registered = nodes.map { false }.toTypedArray()
    val result = hashSetOf<Link>()
    
    val stack = Stack<Node>()
    stack.push(root)
    registered[root.id] = true
    
    fun work() {
      val current = stack.peek()
      result.addAll(current.links)
      
      if (stack.size <= k + 1) {
        val unregisteredNeighbors = current.links.map { (it.n1 to it.n2).otherThan(current) }.filter { !registered[it.id] }.toSet()
        
        unregisteredNeighbors.forEach {
          registered[it.id] = true
          stack.push(it)
          work()
          stack.pop()
        }
      }
    }
    
    work()
    return result
  }
  
  fun getEstablishedEntanglements(n1: Node, n2: Node): MutableList<Path> {
    val stack = Stack<Pair<Link?, Node>>()
    stack.push(null to n1)
    
    val result = mutableListOf<Path>()
    
    while (stack.isNotEmpty()) {
      val (incoming, current) = stack.pop()
      
      if (current == n2) {
        val path = mutableListOf<Node>(n2)
        var inc = incoming!!
        while (inc.n1 != n1 && inc.n2 != n1) {
          val prev = if (inc.n1 == path.last()) inc.n2 else inc.n1
          inc = prev.internalLinks.first { it.contains(inc) }.otherThan(inc)
          path.add(prev)
        }
        path.add(n1)
        result.add(path.reversed())
        continue
      }
      
      val outgoingLinks = if (incoming == null) {
        current.links.filter { it.entangled && !it.swappedAt(current) }
      } else {
        current.internalLinks.filter { it.contains(incoming) }.map { it.otherThan(incoming) }
      }
      
      for (l in outgoingLinks) {
        stack.push(l to Pair(l.n1, l.n2).otherThan(current))
      }
    }
    
    return result
  }
  
  fun widthPhase2(path: Path) = listOf(path[0].remainingQubits, path.last().remainingQubits,
    path.dropLast(1).drop(1).map { it.remainingQubits }.min()?.div(2) ?: Int.MAX_VALUE,
    path.dropLast(1).zip(path.drop(1)).map { (n1, n2) ->
      n1.links.count { (it.n1 == n2 || it.n2 == n2) && !it.assigned }
    }.min()!!
  ).min()!!
  
  fun widthPhase4(path: Path) =
    path.dropLast(1).zip(path.drop(1)).map { (n1, n2) ->
      n1.links.count { (it.n1 == n2 || it.n2 == n2) && it.entangled && it.notSwapped() }
    }.min()!!
  
  fun e(path: Path, mul: Int, oldP: DoubleArray): Double {
    val s = path.size - 1
    val P = DoubleArray(mul + 1, { 0.0 })
    val p = listOf(0.0) + path.edges().map { Math.E.pow(-alpha * +(it.n1.loc - it.n2.loc)) }
    
    var start = s
    if (oldP.sum() == 0.0) {
      (0..mul).forEach { m -> oldP[m] = C_(mul, m) * p[1].pow(m) * (1 - p[1]).pow(mul - m) }
      start = 2
    }
    
    require({ oldP.size == mul + 1 })
    
    (start..s).forEach { k ->
      (0..mul).forEach { i ->
        val exactlyM = C_(mul, i) * p[k].pow(i) * (1 - p[k]).pow(mul - i)
        val atLeastM = ((i + 1)..mul).map { i -> C_(mul, i) * p[k].pow(i) * (1 - p[k]).pow(mul - i) }.sum() + exactlyM
        
        P[i] = oldP[i] * atLeastM + exactlyM * (i + 1..mul).fold(0.0, { acc, i -> acc + oldP[i] })
      }
      
      (0..mul).forEach { m -> oldP[m] = P[m] }
    }
    
    require({ Math.abs(oldP.sum() - 1.0) < 0.0001 })
    return (1..mul).map { m -> m * oldP[m] }.sum() * q.pow(s - 1)
  }
  
  fun inferredE(path: Path, k: Int, dst: Node, P_: Array<Double>): Double {
    val s = maxSearchHops
    val currentHops = path.size - 1
    
    val P = Array(k + 1, { 0.0 })
    val oldP = P_.clone()
    
    val p = Math.E.pow(-alpha * +(dst.loc - path.last().loc) / (s - currentHops))
    
    (path.size..s).forEach { l ->
      (0..k).forEach { m ->
        val exactlyM = C_(k, m) * p.pow(m) * (1 - p).pow(k - m)
        val atLeastM = ((m + 1)..k).map { i -> C_(k, i) * p.pow(i) * (1 - p).pow(k - i) }.sum() + exactlyM
        
        P[m] = oldP[m] * atLeastM + exactlyM * (m + 1..k).fold(0.0, { acc, i -> acc + oldP[i] })
      }
      
      (0..k).forEach { m -> oldP[m] = P[m] }
    }
    
    return (1..k).map { m -> m * oldP[m] }.sum() * q.pow(s - 1)
  }
  
  fun shortestPath(edges: Collection<Edge>, src: Node, dst: Node, fStateMetric: ReducibleLazyEvaluation<Edge, Double> =
    ReducibleLazyEvaluation({ +(it.n1.loc - it.n2.loc) + internalLength })
  ): Pair<Double, Path> {
    val neighborsOf: Map<Node, List<Pair<Node, Double>>> = (edges + edges.map { it.n2 to it.n1 }).map { it.toList() }.groupBy { it.first() }.map { it.key to it.value.map { it[1] to fStateMetric[it[0] to it[1]] } }.toMap()
    
    val prevFromSrc: HashMap<Node, Node> = hashMapOf()
    
    val D = nodes.map { Double.POSITIVE_INFINITY }.toMutableList()
    val q = PriorityQueue<Edge>(Comparator { (o1, _), (o2, _) -> D[o1.id].compareTo(D[o2.id]) })
    
    D[src.id] = 0.0
    q.offer(src to sentinal)
    
    while (q.isNotEmpty()) {
      val (w, prev) = q.poll()  // invariant: top of q reveals the nearest unrecorded node to src
      if (w in prevFromSrc) continue  // skip same node suboptimal paths
      prevFromSrc[w] = prev // record
      
      if (w == dst) {
        val path = LinkedList<Node>()
        
        var cur = dst
        while (cur != sentinal) {
          path.addFirst(cur)
          cur = prevFromSrc[cur]!!
        }
        
        return D[dst.id] to path.toList()
      }
      
      (neighborsOf[w] ?: listOf()).forEach {
        val (neighbor, weight) = it
        val newDist = D[w.id] + weight
        val oldDist = D[neighbor.id]
        if (oldDist > newDist) {
          D[neighbor.id] = newDist
          q.offer(neighbor to w)
        }
      }
    }
    
    return Double.MAX_VALUE to listOf()
  }
  
  val edges = links.groupBy { it.n1 to it.n2 }.map { it.key }
  
  fun linksBetween(n1: Node, n2: Node) = n1.links.filter { it.contains(n2) }
  fun linksBetween(p: Edge) = linksBetween(p.n1, p.n2)
  
  val hopsAway = ReducibleLazyEvaluation<Edge, Int>({ (n1, n2) ->
    shortestPath(edges, n1, n2, ReducibleLazyEvaluation({ 1.0 })).second.size - 1
  }, { (n1, n2) ->
    if (n1.id > n2.id) n2 to n1
    else n1 to n2
  })
  
  fun getAllRoutes(n1_: Int, n2_: Int): List<List<Int>> {
    val (n1, n2) = listOf(min(n1_, n2_), max(n1_, n2_))
    
    val topoStr = links.map { it.n1 to it.n2 }.distinct().joinToString("\n")
    val digest = MessageDigest.getInstance("SHA-256").digest(topoStr.toByteArray(StandardCharsets.UTF_8))
    val result = routeStorage["${Base64.getEncoder().encodeToString(digest)}-$n1-$n2"]
    
    val range = kHopNeighbors(nodes[n1], (hopLimit + 1) / 2) + kHopNeighbors(nodes[n2], (hopLimit + 1) / 2)
    
    if (result.isEmpty()) {
      // find ALL
      fun dfs(l: MutableList<Int>, remainingNeighbors: Set<Node>) {
        if (l.last() == n2) {
          result.add(l.toList())
        } else if (l.size > hopLimit) {
          return
        } else {
          nodes[l.last()].neighbors.filter { it in range }.map { it.id }.forEach {
            if (it !in l && remainingNeighbors.isNotEmpty()) {
              l.add(it)
              dfs(l, remainingNeighbors - nodes[it])
              l.removeAt(l.size - 1)
            }
          }
        }
      }
      
      dfs(mutableListOf(n1), nodes[n2].neighbors - nodes[n1])
      
      // sort via dijkstra
      result.sortByDescending { l ->
        val p = l.map { nodes[it] }
        val m = widthPhase2(p)
        e(p, m, DoubleArray(m, { 0.0 }))
      }
    }
    
    return result
  }
  
  fun buildAllRoutes() {
    (0 until n).toList().combinations(2).forEach { (n1, n2) ->
      getAllRoutes(n1, n2)
    }
    
    saveRoutes()
  }
  
  fun getAllElementCycles(): List<Path> {
    val topoStr = links.map { it.n1 to it.n2 }.distinct().joinToString("\n")
    val digest = MessageDigest.getInstance("SHA-256").digest(topoStr.toByteArray(StandardCharsets.UTF_8))
    
    val result = routeStorage[Base64.getEncoder().encodeToString(digest)]
    
    if (result.isEmpty()) {
      val resultSet = HashSet<List<Int>>()
      
      nodes.forEach { n ->
        (3..10).forEach { len ->
          fun dfs(l: MutableList<Node>) {
            val tmp = l.last().neighbors.intersect(l)
            
            if (l.size == len) {
              if (tmp == setOf(l[l.size - 2], n)) {
                val l = l.map { it.id }
                val m = l.indexOf(l.min())
                resultSet.add(l.subList(m, l.size) + l.subList(0, m))
              }
            } else if (tmp.size <= 1) {
              l.last().neighbors.filter { l.size == 1 || it != l[l.size - 2] }.forEach {
                l.add(it)
                dfs(l)
                l.removeAt(l.size - 1)
              }
            }
          }
          
          dfs(mutableListOf(n))
        }
      }
      
      result.addAll(resultSet)
      
      saveRoutes()
    }
    
    return result.map { it.map { nodes[it] } }
  }
  
  companion object {
    val hopLimit = 15
    
    val routeStorage = ReducibleLazyEvaluation<String, MutableList<List<Int>>>({ mutableListOf() }, { it }, { k, v -> v })
    
    init {
      val file = File("./dist/route_storage.txt")
      file.parentFile.mkdirs()
      file.createNewFile()
      
      file.readLines().map {
        val tmp = it.split(" ")
        routeStorage[tmp[0]].add(tmp.drop(1).map { it.toInt() })
      }
    }
    
    fun saveRoutes() {
      val file = File("./dist/route_storage.txt")
      file.parentFile.mkdirs()
      
      file.writeText(routeStorage.entries.map { (k, v) ->
        v.map { "$k ${it.joinToString(" ")}\n" }.joinToString("")
      }.joinToString(""))
    }
    
    fun generate(n: Int, q: Double, k: Int, a: Double, degree: Int): Topo {
      val alpha: Double = a
      val nodeLocs = mutableListOf<DoubleArray>()
      val links = mutableListOf<Pair<Int, Int>>()
      
      val controllingD = sqrt(edgeLen * edgeLen / n)
      while (nodeLocs.size < n) {
        val element = DoubleArray(2, { randGen.nextDouble() * edgeLen })
        if (nodeLocs.all { +(it - element) > controllingD / 1.2 })
          nodeLocs.add(element)
      }
      
      nodeLocs.sortBy { it[0] + (it[1] * 10 / edgeLen).toInt() * 1000000 }
      
      val beta = dynSearch(0.0, 20.0, degree.toDouble(), { beta ->
        links.clear()
        (0 until n).toList().combinations(2).forEach { (n1, n2) ->
          val (l1, l2) = listOf(n1, n2).map { nodeLocs[it] }
          val d = +(l2 - l1)
          
          if (d < 2 * controllingD) {
            val l = (1..50).map { randGen.nextDouble() }.min()!!
            val r = Math.E.pow(-beta * d)
            if (l < r) {
              links.add(n1 to n2)
            }
          }
        }
        
        2 * links.size.toDouble() / n
      }, false, 0.2)
      
      val disjointSet = DisjointSet(n)
      
      links.forEach { (n1, n2) -> disjointSet.merge(n1, n2) }
      val ccs = (0 until n).map { it to disjointSet.getRepresentative(it) }.map { (id, p) -> id to p }.groupBy { it.second }.map { it.value.map { it.first } }.sortedBy { -it.size }
      
      val biggest = ccs[0]
      
      ccs.subList(1, ccs.size).forEach {
        it.shuffled(randGen).take(3).forEach { toConnect ->
          val nearest = biggest.minBy { +(nodeLocs[it] - nodeLocs[toConnect]) }!!
          val tmp = listOf(nearest, toConnect).sorted()
          links.add(tmp[0] to tmp[1])
        }
      }
      
      links.flatMap { it.toList() }.groupBy { it }.forEach { id, occ ->
        if (occ.size / 2 < 5) {
          val nearest = (0 until n).sortedBy { +(nodeLocs[it] - nodeLocs[id]) }.take(6 - occ.size / 2).drop(1)
          links.addAll(nearest.map {
            val tmp = listOf(it, id).sorted()
            tmp[0] to tmp[1]
          })
        }
      }
      
      return Topo("""
$n
$alpha
$q
$k
${nodeLocs.mapIndexed { id, loc -> "${(randGen.nextDouble() * 5 + 10).toInt()} ${loc.joinToString(" ")}" }.joinToString("\n")}
${links.map { "${it.first} ${it.second} ${(randGen.nextDouble() * 5 + 3).toInt()}" }.joinToString("\n")}
      """.trimIndent())
    }
  }
}

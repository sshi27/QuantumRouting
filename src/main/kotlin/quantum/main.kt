package quantum

import quantum.algorithm.*
import quantum.topo.Topo
import utils.*
import java.awt.event.WindowEvent
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.swing.WindowConstants
import kotlin.math.pow

fun sim() {
  val topos = (nList * dList).map { (n, d) ->
    n to d to Topo.generate(n, 0.9, 5, 0.1, d)
  }.toMap()
  
  val alphaStore = ReducibleLazyEvaluation<Pair<Topo, Double>, Double>({ (topo, p) ->
    dynSearch(1E-10, 1.0, p, { x ->
      topo.links.map { Math.E.pow(-x * +(it.n1.loc - it.n2.loc)) }.sum() / topo.links.size
    }, false, 0.001)
  })
  val repeat = 1000
  
  topoRange.forEach { topoIdx ->
    val executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
    
    allAvailableSettings.forEach { (d, n, p, q, k, nsd) ->
      val topo = topos[n to d]!!
      val alpha = alphaStore[topo to p]
      
      val testSet = (1..repeat).map {
        (0 until n).shuffled(randGen).take(2 * nsd).chunked(2).map { it.toPair() }
      }
      
      val algorithms = synchronized(topo) {
        topo.q = q
        topo.k = k
        topo.alpha = alpha
        
        val algorithms = mutableListOf(
          OnlineAlgorithm(Topo(topo)),
          OnlineAlgorithm(Topo(topo), false),
          CreationRate(Topo(topo)),
          SingleLink(Topo(topo)),
          GreedyGeographicRouting(Topo(topo)),
          GreedyHopRouting(Topo(topo))
        )
        
        if (n == nList.first() && q == qList.first() && p == pList.first() && d == dList.first() && k == kList.first()) {
          algorithms.addAll(listOf(
            BotCap(Topo(topo)),
            SumDist(Topo(topo)), MultiMetric(Topo(topo)),
            
            BotCap(Topo(topo), false), CreationRate(Topo(topo), false),
            SumDist(Topo(topo), false), MultiMetric(Topo(topo), false),
            SingleLink(Topo(topo), false)
          ))
        }
        
        algorithms.filter {
          val done = try {
            File("dist/${id(n, topoIdx, q, k, p, d, nsd, it.name)}.txt").readLines().drop(2).any { it.startsWith("--") }
          } catch (e: Exception) {
            false
          }
          if (done)
            println("skip ${id(n, topoIdx, q, k, p, d, nsd, it.name)}")
          !done
        }
      }
      
      algorithms.forEach { solver ->
        solver.settings = id(n, topoIdx, q, k, p, d, nsd, solver.name)
        val fn = "dist/${solver.settings}.txt"
        
        executor.execute {
          val topo = solver.topo
          println(topo.getStatistics())
          
          solver.logWriter = BufferedWriter(FileWriter(fn))
          
          testSet.forEach {
            solver.work(it.map { Pair(topo.nodes[it.first], topo.nodes[it.second]) })
          }
          
          solver.logWriter.appendln("-----------")
          solver.logWriter.appendln(topo.toString())
          solver.logWriter.appendln(topo.getStatistics())
          solver.logWriter.close()
        }
      }
    }
    
    executor.shutdown()
    executor.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS)
  }
}

fun simpleTest() {
  val netTopology = Topo.generate(50, 0.9, 5, 0.1, 6)
  
  val p = listOf(0.8, 0.5)
  val alphas = p.map { expectedAvgP ->
    var alpha = 0.1
    var step = 0.1
    var lastAdd = true
    
    while (true) {
      val topo = Topo(netTopology.toString().lines().mapIndexed { i, line -> if (i == 1) alpha.toString() else line }.joinToString("\n"))
      val avgP = topo.links.map { Math.E.pow(-alpha * +(it.n1.loc - it.n2.loc)) }.sum() / topo.links.size
      
      if (Math.abs(avgP - expectedAvgP) / expectedAvgP < 0.001) break
      else if (avgP > expectedAvgP) {
        if (!lastAdd) step /= 2
        alpha += step
        lastAdd = true
      } else {
        if (lastAdd) step /= 2
        alpha -= step
        lastAdd = false
      }
    }
    
    alpha
  }
  
  val repeat = 1000
  
  val testSet = (1..10).map { nsd ->
    val combinations = netTopology.nodes.combinations(2)
    (1..repeat).map {
      combinations.shuffled(randGen).take(nsd).map { it[0].id to it[1].id }
    }
  }
  
  val children = mutableListOf<String>()
  
  p.zip(alphas).forEach { (p, a) ->
    val topo = Topo(netTopology.toString().lines().mapIndexed { i, line -> if (i == 1) a.toString() else line }.joinToString("\n"))
    println(topo.getStatistics())
    
    val algorithms = listOf(
      OnlineAlgorithm(Topo(topo))
//      , OnlineAlgorithmWithRecoveryPaths(Topo(topo))
//      , BotCap(Topo(topo)),  CreationRate(Topo(topo)),
//      SumDist(Topo(topo)),
//      SingleLink(Topo(topo))
//      , GreedyGeographicRouting(Topo(topo))
    )
    
    algorithms.forEach {
      it.logWriter = BufferedWriter(FileWriter("""dist/test-${it.name}.txt"""))
    }
    
    val results: MutableList<MutableList<Pair<Double, Double>>> = MutableList(algorithms.size, { mutableListOf<Pair<Double, Double>>() })
    algorithms.parallelStream().forEach { solver ->
      val topo = solver.topo
      
      var window: Visualizer? = null
      
      if (visualize) {
        window = Visualizer(solver)
        window.title = "Routing Quantum Entanglement - ${solver.name}"
        window.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE)
      }
      
      solver.prepare()
      
      results[algorithms.indexOf(solver)].addAll(
        testSet.drop(1).map {
          val result = it.map {
            solver.work(it.map { Pair(topo.nodes[it.first], topo.nodes[it.second]) })
          }
          val avgEntanglements = result.sumBy { it.first }.toDouble() / result.size
          val avgEntangled = result.sumBy { it.second }.toDouble() / result.size
          
          avgEntanglements to avgEntangled
        }
      )
      
      if (visualize)
        window?.dispatchEvent(WindowEvent(window, WindowEvent.WINDOW_CLOSING))
    }
    
    algorithms.forEach {
      it.logWriter.appendln("-----------")
      it.logWriter.appendln(topo.toString())
      it.logWriter.appendln(topo.getStatistics())
    }
    
    children += listOf("""
    {
      'name': 'num-ebit-pairs-${topo.n}-${p}-${topo.q}',
      'solutionList': ${algorithms.map { """"${it.name}"""" }},
      'xTitle': '\\# src-dst pairs in one time slot',
      'yTitle': '\\# success pairs',
      'x': ${(1..results[0].size).toList()},
      'y': ${results.map { it.map { it.first } }},
    }""".trimIndent(), """
    {
      'name': 'num-succ-pairs-${topo.n}-${p}-${topo.q}',
      'solutionList': ${algorithms.map { """"${it.name}"""" }},
      'xTitle': '\\# src-dst pairs in one time slot',
      'yTitle': '\\# entanglements',
      'x': ${(1..results[0].size).toList()},
      'y': ${results.map { it.map { it.second } }},
    }""".trimIndent()
    )
    
    File("../plot/last-plot-data.json").writeText(
      """
{
  'type': "line",
  'figWidth': 600,
  'figHeight': 350,
  'usetex': True,

  'legendLoc': 'best',
  'legendColumn': 1,

  'markerSize': 8,
  'lineWidth': 1,

  'xLog': False,
  'yLog': False,
  'xGrid': True,
  'yGrid': True,

  'xFontSize': 16,
  'xTickRotate': False,
  'yFontSize': 16,
  'legendFontSize': 14,
  'output': True,

  'children': $children
}
""")
  }
}

var visualize = true

class Main {
  companion object {
    @JvmStatic
    fun main(args: Array<String>) {
      visualize = try {
        val solver = OnlineAlgorithm(Topo.generate(50, 0.9, 5, 0.1, 6))
        solver.logWriter = BufferedWriter(FileWriter("""dist/test-gui.txt"""))
        
        val window = Visualizer(solver)
        window.title = "Routing Quantum Entanglement - ${solver.name}"
        window.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE)
        
        window.dispatchEvent(WindowEvent(window, WindowEvent.WINDOW_CLOSING))
        true
      } catch (e: java.lang.Exception) {
        print("WARNING: GUI not available. Ignore this? \n(The final results are the same. Only difference: \n you CANNOT see the network visualizer) [Y/N]")
        val scanner = Scanner(System.`in`)
        if (scanner.next().first().toLowerCase() == 'y')
          false
        else throw Exception("No GUI available, and user refuses to continue. ")
      }
      
      simpleTest()
      try {
        val l = args.map { it.toInt() }
        if (l.isNotEmpty()) nList = l
      } catch (e: Exception) {
      }
      sim()
    }
  }
}

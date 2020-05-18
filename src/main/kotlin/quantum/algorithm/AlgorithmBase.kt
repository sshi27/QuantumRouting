package quantum.algorithm

import quantum.topo.Edge
import quantum.topo.Node
import quantum.topo.Path
import quantum.topo.Topo
import utils.require
import java.io.BufferedWriter
import java.io.Writer

abstract class Algorithm(val topo: Topo) {
  abstract val name: String
  lateinit var logWriter: BufferedWriter
  var settings: String = "Simple"
  
  val srcDstPairs: MutableList<Pair<Node, Node>> = mutableListOf()
  
  fun work(pairs: List<Pair<Node, Node>>): Pair<Int, Int> {
    require({ topo.isClean() })
    srcDstPairs.clear()
    srcDstPairs.addAll(pairs)
  
    val pairs = srcDstPairs.map { "${it.first.id}⟷${it.second.id}" }
//    println("""[$settings] Establishing: $pairs""".trimIndent())
    logWriter.appendln(pairs.joinToString())
    
    P2()
    
    tryEntanglement()
    
    P4()
    
    val established = srcDstPairs.map { (n1, n2) -> n1 to n2 to topo.getEstablishedEntanglements(n1, n2) }
    
//    established.forEach {
//      it.second.forEach {
//        println("Established path: ${it}")
//      }
//    }
    println("""[$settings] Established: ${established.map { "${it.first.first.id}⟷${it.first.second.id} × ${it.second.size}" }} - $name""".trimIndent())
    
    topo.clearEntanglements()
    return established.count { it.second.isNotEmpty() } to established.sumBy { it.second.size }
  }
  
  fun tryEntanglement() {
    topo.links.forEach { it.tryEntanglement() }
  }
  
  abstract fun prepare()
  
  abstract fun P2()
  
  abstract fun P4()
}

typealias PickedPath = Triple<Double, Int, Path>

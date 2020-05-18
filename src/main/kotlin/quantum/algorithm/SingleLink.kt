package quantum.algorithm

import quantum.topo.Topo
import quantum.topo.to

class SingleLink(topo: Topo, allowRecoveryPaths: Boolean = true) : OnlineAlgorithm(run {
  topo.links = topo.links.groupBy { it.n1 to it.n2 }.map { it.value.first() }.toMutableList()
  Topo(topo.toString())
}, allowRecoveryPaths) {
  override val name: String = "SL" + if (allowRecoveryPaths) "" else "-R"
}

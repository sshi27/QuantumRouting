package quantum

import utils.ReducibleLazyEvaluation
import java.io.File

class Plot {
  val children = mutableListOf<String>()
  
  fun plot() {
    File("../plot/last-plot-data.json").writeText(
      """
{
  "type": "line",
  "figWidth": 600,
  "figHeight": 350,
  "usetex": false,

  "legendLoc": "best",
  "legendColumn": 1,

  "markerSize": 8,
  "lineWidth": 1,

  "xLog": false,
  "yLog": false,
  "xGrid": false,
  "yGrid": false,

  "xFontSize": 22,
  "xTickRotate": false,
  "yFontSize": 22,
  "legendFontSize": 14,
  "output": true,
  "show": false,

  "children": $children
}
"""
    )
  }
  
  val nameMapping = mapOf("SL" to "SLMP", "Online" to "Q-CAST", "Online-R" to "Q-CAST\\\\R", "CR" to "Q-PASS", "CR-R" to "Q-PASS\\\\R", "Greedy_H" to "Greedy")
  val names = listOf("Online", "SL", "Greedy_H", "CR")
  
  fun throughputCdf() {
    val nameMapping = mapOf("SL" to "SLMP", "Online" to "Q-CAST", "Greedy_H" to "Greedy")
    val names = listOf("Online", "SL", "Greedy_H", "CR", "BotCap", "SumDist", "MultiMetric")
    
    (1..3).forEach { mode ->
      var (d, n, p, q, k, nsd) = referenceSetting
      if (mode == 2) p = 0.3
      else if (mode == 3) n = 400
      
      var max = 0
      
      val result = names.map { name ->
        var s = 0
        val sum = ReducibleLazyEvaluation<Int, Int>({ 0 })
        
        topoRange.flatMap { topoIdx ->
          parseLog("dist/" + id(n, topoIdx, q, k, p, d, nsd, name) + ".txt")
        }.map { it.majorPaths.sumBy { it.succ } }.groupBy { it }.map { (k, v) ->
          k to v.size
        }.sortedBy { it.first }.forEach { (k, v) ->
          max = Math.max(max, k)
          s += v
          (k..1000).forEach { i -> sum[i] = s }
        }
        
        (0..max).map { sum[it] / s.toDouble() }
      }
      
      children.add("""
        {
          "markerSize": 0,
          "name": "${"throughput-cdf-$d-$n-$p-$q-$k-$nsd".replace(".", "")}",
          "solutionList": ${names.map { """ "${nameMapping[it] ?: it}" """ }},
          "xTitle": "Throughput (eps)",
          "xTicks&Labels": ${(0..max).chunked(5).map { it.first() }},
          
          "xLimit": [0, $max],
          "yLimit": [0, 1],
          "x": ${(0..max).toList()},
          "y": ${result.map { l -> if (l.size < max + 1) l + List(max + 1 - l.size, { 1.0 }) else l }}
        }""".trimIndent())
    }
  }
  
  fun throughputNsd() {
    val (d, n, p, q, k, nsd) = referenceSetting
    val results = names.map { name ->
      nsdList.sorted().map { nsd ->
        val rlist = topoRange.flatMap { topoIdx ->
          parseLog("dist/" + id(n, topoIdx, q, k, p, d, nsd, name) + ".txt")
        }
        
        rlist.map { it.majorPaths.sumByDouble { it.succ.toDouble() } }.average()
      }
    }
    
    val results2 = names.map { name ->
      nsdList.sorted().map { nsd ->
        val rlist = topoRange.flatMap { topoIdx ->
          parseLog("dist/" + id(n, topoIdx, q, k, p, d, nsd, name) + ".txt")
        }
        
        rlist.map { it.majorPaths.count { it.succ > 0 }.toDouble() }.average()
      }
    }
    
    val results3 = names.map { name ->
      nsdList.sorted().map { nsd ->
        val rlist = topoRange.flatMap { topoIdx ->
          parseLog("dist/" + id(n, topoIdx, q, k, p, d, nsd, name) + ".txt")
        }
        
        rlist.map { it.majorPaths.filter { it.succ > 0 }.distinctBy { it.path.first() to it.path.last() }.size.toDouble() }.average()
      }
    }
    
    children += listOf(
      """
        {
          "name": "${"throughput-$d-$n-$p-$q-$k-nsd".replace(".", "")}",
          "solutionList": ${names.map { """ "${nameMapping[it] ?: it}" """ }},
          "xTitle": "# S-D pairs in one time slot",
          "yTitle": "Throughput (eps)",
          "x": $nsdList,
          "y": ${results}
        }""".trimIndent(), """
        {
          "name": "${"succ-paths-$d-$n-$p-$q-$k-nsd".replace(".", "")}",
          "solutionList": ${names.map { """ "${nameMapping[it] ?: it}" """ }},
          "xTitle": "# S-D pairs in one time slot",
          "yTitle": "# succ main paths",
          "x": $nsdList,
          "y": ${results2}
        }""".trimIndent(), """
        {
          "name": "${"succ-pairs-$d-$n-$p-$q-$k-nsd".replace(".", "")}",
          "solutionList": ${names.map { """ "${nameMapping[it] ?: it}" """ }},
          "xTitle": "# S-D pairs in one time slot",
          "yTitle": "# succ S-D pairs",
          "x": $nsdList,
          "y": ${results3}
        }""".trimIndent()
    )
  }
  
  fun throughputD() {
    val (d, n, p, q, k, nsd) = referenceSetting
    val results = names.map { name ->
      dList.sorted().map { d ->
        val rlist = topoRange.flatMap { topoIdx ->
          parseLog("dist/" + id(n, topoIdx, q, k, p, d, nsd, name) + ".txt")
        }
        
        rlist.map { it.majorPaths.sumByDouble { it.succ.toDouble() } }.average()
      }
    }
    
    val results2 = names.map { name ->
      dList.sorted().map { d ->
        val rlist = topoRange.flatMap { topoIdx ->
          parseLog("dist/" + id(n, topoIdx, q, k, p, d, nsd, name) + ".txt")
        }
        
        rlist.map { it.majorPaths.count { it.succ > 0 }.toDouble() }.average()
      }
    }
    
    val results3 = names.map { name ->
      dList.sorted().map { d ->
        val rlist = topoRange.flatMap { topoIdx ->
          parseLog("dist/" + id(n, topoIdx, q, k, p, d, nsd, name) + ".txt")
        }
        
        rlist.map { it.majorPaths.filter { it.succ > 0 }.distinctBy { it.path.first() to it.path.last() }.size.toDouble() }.average()
      }
    }
    
    children += listOf(
      """
        {
          "name": "${"throughput-d-$n-$p-$q-$k-$nsd".replace(".", "")}",
          "xTitle": "Average node degree",
          "x": ${dList.sorted()},
          "solutionList": ${names.map { """ "${nameMapping[it] ?: it}" """ }},
          "yTitle": "Throughput (eps)",
          "y": ${results}
        }""".trimIndent(), """
        {
          "name": "${"succ-paths-d-$n-$p-$q-$k-$nsd".replace(".", "")}",
          "xTitle": "Average node degree",
          "x": ${dList.sorted()},
          "solutionList": ${names.map { """ "${nameMapping[it] ?: it}" """ }},
          "yTitle": "# succ main paths",
          "y": ${results2}
        }""".trimIndent(), """
        {
          "name": "${"succ-pairs-d-$n-$p-$q-$k-$nsd".replace(".", "")}",
          "xTitle": "Average node degree",
          "x": ${dList.sorted()},
          "solutionList": ${names.map { """ "${nameMapping[it] ?: it}" """ }},
          "yTitle": "# succ S-D pairs",
          "y": ${results3}
        }""".trimIndent()
    )
  }
  
  fun throughputP() {
    val (d, n, p, q, k, nsd) = referenceSetting
    val results = names.map { name ->
      pList.sorted().map { p ->
        val rlist = topoRange.flatMap { topoIdx ->
          parseLog("dist/" + id(n, topoIdx, q, k, p, d, nsd, name) + ".txt")
        }
        
        rlist.map { it.majorPaths.sumByDouble { it.succ.toDouble() } }.average()
      }
    }
    
    val results2 = names.map { name ->
      pList.sorted().map { p ->
        val rlist = topoRange.flatMap { topoIdx ->
          parseLog("dist/" + id(n, topoIdx, q, k, p, d, nsd, name) + ".txt")
        }
        
        rlist.map { it.majorPaths.count { it.succ > 0 }.toDouble() }.average()
      }
    }
    
    val results3 = names.map { name ->
      pList.sorted().map { p ->
        val rlist = topoRange.flatMap { topoIdx ->
          parseLog("dist/" + id(n, topoIdx, q, k, p, d, nsd, name) + ".txt")
        }
        
        rlist.map { it.majorPaths.filter { it.succ > 0 }.distinctBy { it.path.first() to it.path.last() }.size.toDouble() }.average()
      }
    }
    
    children += listOf(
      """
        {
          "name": "${"throughput-$d-$n-p-$q-$k-$nsd".replace(".", "")}",
          "xTitle": "Average channel success rate",
          "x": ${pList.sorted()},
          "solutionList": ${names.map { """ "${nameMapping[it] ?: it}" """ }},
          "yTitle": "Throughput (eps)",
          "y": ${results}
        }""".trimIndent(), """
        {
          "name": "${"succ-paths-$d-$n-p-$q-$k-$nsd".replace(".", "")}",
          "xTitle": "Average channel success rate",
          "x": ${pList.sorted()},
          "solutionList": ${names.map { """ "${nameMapping[it] ?: it}" """ }},
          "yTitle": "# succ main paths",
          "y": ${results2}
        }""".trimIndent(), """
        {
          "name": "${"succ-pairs-$d-$n-p-$q-$k-$nsd".replace(".", "")}",
          "xTitle": "Average channel success rate",
          "x": ${pList.sorted()},
          "solutionList": ${names.map { """ "${nameMapping[it] ?: it}" """ }},
          "yTitle": "# succ S-D pairs",
          "y": ${results3}
        }""".trimIndent()
    )
  }
  
  fun throughputN() {
    val (d, n, p, q, k, nsd) = referenceSetting
    val results = names.map { name ->
      nList.sorted().map { n ->
        val rlist = topoRange.flatMap { topoIdx ->
          parseLog("dist/" + id(n, topoIdx, q, k, p, d, nsd, name) + ".txt")
        }
        
        rlist.map { it.majorPaths.sumByDouble { it.succ.toDouble() } }.average()
      }
    }
    
    val results2 = names.map { name ->
      nList.sorted().map { n ->
        val rlist = topoRange.flatMap { topoIdx ->
          parseLog("dist/" + id(n, topoIdx, q, k, p, d, nsd, name) + ".txt")
        }
        
        rlist.map { it.majorPaths.count { it.succ > 0 }.toDouble() }.average()
      }
    }
    
    val results3 = names.map { name ->
      nList.sorted().map { n ->
        val rlist = topoRange.flatMap { topoIdx ->
          parseLog("dist/" + id(n, topoIdx, q, k, p, d, nsd, name) + ".txt")
        }
        
        rlist.map { it.majorPaths.filter { it.succ > 0 }.distinctBy { it.path.first() to it.path.last() }.size.toDouble() }.average()
      }
    }
    
    children += listOf(
      """
        {
          "name": "${"throughput-$d-n-$p-$q-$k-$nsd".replace(".", "")}",
          "solutionList": ${names.map { """ "${nameMapping[it] ?: it}" """ }},
          "xTitle": "|V|",
          "xLog" : true,
          "xTicks&Labels": [${nList.sorted()}, ${nList.sorted().map { """ "$it" """ }}],
          "yTitle": "Throughput (eps)",
          "x": ${nList.sorted()},
          "y": ${results}
        }""".trimIndent(), """
        {
          "name": "${"succ-paths-$d-n-$p-$q-$k-$nsd".replace(".", "")}",
          "solutionList": ${names.map { """ "${nameMapping[it] ?: it}" """ }},
          "xTitle": "|V|",
          "xLog" : true,
          "xTicks&Labels": [${nList.sorted()}, ${nList.sorted().map { """ "$it" """ }}],
          "yTitle": "# succ main paths",
          "x": ${nList.sorted()},
          "y": ${results2}
        }""".trimIndent(), """
        {
          "name": "${"succ-pairs-$d-n-$p-$q-$k-$nsd".replace(".", "")}",
          "solutionList": ${names.map { """ "${nameMapping[it] ?: it}" """ }},
          "xTitle": "|V|",
          "xLog" : true,
          "xTicks&Labels": [${nList.sorted()}, ${nList.sorted().map { """ "$it" """ }}],
          "yTitle": "# succ S-D pairs",
          "x": ${nList.sorted()},
          "y": ${results3}
        }""".trimIndent()
    )
  }
  
  fun throughputQ() {
    val (d, n, p, q, k, nsd) = referenceSetting
    val results = names.map { name ->
      qList.sorted().map { q ->
        val rlist = topoRange.flatMap { topoIdx ->
          parseLog("dist/" + id(n, topoIdx, q, k, p, d, nsd, name) + ".txt")
        }
        
        rlist.map { it.majorPaths.sumByDouble { it.succ.toDouble() } }.average()
      }
    }
    
    val results2 = names.map { name ->
      qList.sorted().map { q ->
        val rlist = topoRange.flatMap { topoIdx ->
          parseLog("dist/" + id(n, topoIdx, q, k, p, d, nsd, name) + ".txt")
        }
        
        rlist.map { it.majorPaths.count { it.succ > 0 }.toDouble() }.average()
      }
    }
    
    val results3 = names.map { name ->
      qList.sorted().map { q ->
        val rlist = topoRange.flatMap { topoIdx ->
          parseLog("dist/" + id(n, topoIdx, q, k, p, d, nsd, name) + ".txt")
        }
        
        rlist.map { it.majorPaths.filter { it.succ > 0 }.distinctBy { it.path.first() to it.path.last() }.size.toDouble() }.average()
      }
    }
    
    children += listOf(
      """
        {
          "name": "${"throughput-$d-$n-$p-q-$k-$nsd".replace(".", "")}",
          "xTitle": "Swapping success rate",
          "x": ${qList.sorted()},
          "solutionList": ${names.map { """ "${nameMapping[it] ?: it}" """ }},
          "yTitle": "Throughput (eps)",
          "y": ${results}
        }""".trimIndent(), """
        {
          "name": "${"succ-paths-$d-$n-$p-q-$k-$nsd".replace(".", "")}",
          "xTitle": "Swapping success rate",
          "x": ${qList.sorted()},
          "solutionList": ${names.map { """ "${nameMapping[it] ?: it}" """ }},
          "yTitle": "# succ main paths",
          "y": ${results2}
        }""".trimIndent(), """
        {
          "name": "${"succ-pairs-$d-$n-$p-q-$k-$nsd".replace(".", "")}",
          "xTitle": "Swapping success rate",
          "x": ${qList.sorted()},
          "solutionList": ${names.map { """ "${nameMapping[it] ?: it}" """ }},
          "yTitle": "# succ S-D pairs",
          "y": ${results3}
        }""".trimIndent()
    )
  }
  
  fun throughputK() {
    val (d, n, p, q, k, nsd) = referenceSetting
    val results = names.map { name ->
      kList.sorted().map { k ->
        val rlist = topoRange.flatMap { topoIdx ->
          parseLog("dist/" + id(n, topoIdx, q, k, p, d, nsd, name) + ".txt")
        }
        
        rlist.map { it.majorPaths.sumByDouble { it.succ.toDouble() } }.average()
      }
    }
    
    val results2 = names.map { name ->
      kList.sorted().map { k ->
        val rlist = topoRange.flatMap { topoIdx ->
          parseLog("dist/" + id(n, topoIdx, q, k, p, d, nsd, name) + ".txt")
        }
        
        rlist.map { it.majorPaths.count { it.succ > 0 }.toDouble() }.average()
      }
    }
    
    val results3 = names.map { name ->
      kList.sorted().map { k ->
        val rlist = topoRange.flatMap { topoIdx ->
          parseLog("dist/" + id(n, topoIdx, q, k, p, d, nsd, name) + ".txt")
        }
        
        rlist.map { it.majorPaths.filter { it.succ > 0 }.distinctBy { it.path.first() to it.path.last() }.size.toDouble() }.average()
      }
    }
    
    children += listOf(
      """
        {
          "name": "${"throughput-$d-$n-$p-$q-k-$nsd".replace(".", "")}",
          "xTitle": "Link state broadcast range k",
          "x": ${listOf(0, 3, 6, 9)},
          "xTicks&Labels": [${listOf(0, 3, 6, 9)}, ["0", "3", "6", "∞"]],
          "solutionList": ${names.map { """ "${nameMapping[it] ?: it}" """ }},
          "yTitle": "Throughput (eps)",
          "y": ${results}
        }""".trimIndent(), """
        {
          "name": "${"succ-paths-$d-$n-$p-$q-k-$nsd".replace(".", "")}",
          "xTitle": "Link state broadcast range k",
          "x": ${listOf(0, 3, 6, 9)},
          "xTicks&Labels": [${listOf(0, 3, 6, 9)}, ["0", "3", "6", "∞"]],
          "solutionList": ${names.map { """ "${nameMapping[it] ?: it}" """ }},
          "yTitle": "# succ main paths",
          "y": ${results2}
        }""".trimIndent(), """
        {
          "name": "${"succ-pairs-$d-$n-$p-$q-k-$nsd".replace(".", "")}",
          "xTitle": "Link state broadcast range k",
          "x": ${listOf(0, 3, 6, 9)},
          "xTicks&Labels": [${listOf(0, 3, 6, 9)}, ["0", "3", "6", "∞"]],
          "solutionList": ${names.map { """ "${nameMapping[it] ?: it}" """ }},
          "yTitle": "# succ S-D pairs",
          "y": ${results3}
        }""".trimIndent()
    )
  }
  
  fun efficiency() {
    (1..3).forEach { mode ->
      val names =
        if (mode == 1) listOf("Online", "SL", "Greedy_H", "CR")
        else if (mode == 2)
          listOf("Online", "SL", "Greedy_H")
        else listOf("Online", "Online-R", "CR", "CR-R")
      
      val (d, n, p, q, k, nsd) = referenceSetting
      var max = 0
      
      val channels = names.map { name ->
        val result = ReducibleLazyEvaluation<Int, Double>({ Double.NaN })
        
        topoRange.flatMap { topoIdx ->
          parseLog("dist/" + id(n, topoIdx, q, k, p, d, nsd, name) + ".txt")
        }.map {
          it.majorPaths.sumBy { it.succ } to it.rpChannelCnt +
              it.majorPaths.sumBy { it.width * (it.path.size - 1) + it.recoveryPaths.sumBy { it.width * (it.path.size - 1) } }
        }.groupBy { it.first }.map {
          max = Math.max(max, it.key)
          result[it.key] = it.value.map { it.second.toDouble() }.average()
        }
        
        (0..max).map { result[it] }
      }
      
      children += """
        {
          "name": "${"channels-throughput-$d-$n-$p-$q-$k-$nsd${if (mode == 1) "" else if (mode == 2) "-noA1" else "-rp"}".replace(".", "")}",
          "solutionList": ${names.map { """ "${nameMapping[it] ?: it}" """ }},
          "xTitle": "Throughput (eps)",
          "yTitle": "# occupied channels",
          "x": ${(0..max).toList()},
          "xTicks&Labels": ${(0..max).chunked(5).map { it.first() }},
          "y": ${channels.map { l -> if (l.size < max + 1) l + List(max + 1 - l.size, { Double.NaN }) else l }}
        }""".trimIndent()
    }
  }
  
  fun fairness() {
    val (d, n, p, q, k, nsd) = referenceSetting
    
    var max = 0
    
    val result = names.map { name ->
      var s = 0
      val sum = ReducibleLazyEvaluation<Int, Int>({ 0 })
      
      topoRange.flatMap { topoIdx ->
        parseLog("dist/" + id(n, topoIdx, q, k, p, d, nsd, name) + ".txt")
      }.flatMap { it.majorPaths.groupBy { it.path.first() to it.path.last() }.map { it.value.sumBy { it.width } } }
        .groupBy { it }.map { (k, v) -> k to v.size }.sortedBy { it.first }.forEach { (k, v) ->
          max = Math.max(max, k)
          s += v
          (k..1000).forEach { i -> sum[i] = s }
        }
      
      (0..max).map { sum[it] / s.toDouble() }
    }
    
    children.add(
      """
        {
          "markerSize": 0,
          "name": "${"mp-cdf-$d-$n-$p-$q-$k-$nsd".replace(".", "")}",
          "solutionList": ${names.map { """ "${nameMapping[it] ?: it}" """ }},
          "xTitle": "Total width of allocated major paths",
          "yTitle": "CDF",
          "xLimit": [0, $max],
          "yLimit": [0, 1],
          "x": ${(0..max).toList()},
          "y": ${result.map { l -> if (l.size < max + 1) l + List(max + 1 - l.size, { 1.0 }) else l }}
        }""".trimIndent()
    )
  }
  
  fun recovery1() {
    val q = qList.first()
    val k = kList.first()
    val d = dList.first()
    
    pList.forEach { p ->
      for (n in nList.sorted()) {
        var deviation = 0
        if (p != pList.first()) deviation++
        if (n != nList.first()) deviation++
        
        if (deviation >= 1) continue
        
        val names = listOf("CR", "CR-R")
        
        val results = names.map { name ->
          nsdList.sorted().map { nsd ->
            val rlist = topoRange.flatMap { topoIdx ->
              parseLog("dist/" + id(n, topoIdx, q, k, p, d, nsd, name) + ".txt")
            }
            
            rlist.map { it.majorPaths.sumByDouble { it.succ.toDouble() } }.average()
          }
        }
        
        val results2 = names.map { name ->
          nsdList.sorted().map { nsd ->
            val rlist = topoRange.flatMap { topoIdx ->
              parseLog("dist/" + id(n, topoIdx, q, k, p, d, nsd, name) + ".txt")
            }
            
            rlist.map { it.majorPaths.count { it.succ > 0 }.toDouble() }.average()
          }
        }
        
        children += listOf(
          """
          {
            "name": "${"a1-rp-throughput-$d-$n-$p-$q-$k-nsd".replace(".", "")}",
            "solutionList": ${names.map { """ "${nameMapping[it] ?: it}" """ }},
            "xTitle": "# S-D pairs in one time slot",
            "yTitle": "Throughput (eps)",
            "x": $nsdList,
            "y": ${results}
          }""".trimIndent(), """
          {
            "name": "${"a1-rp-succ-pairs-$d-$n-$p-$q-$k-nsd".replace(".", "")}",
            "solutionList": ${names.map { """ "${nameMapping[it] ?: it}" """ }},
            "xTitle": "# S-D pairs in one time slot",
            "yTitle": "# succ S-D pairs",
            "x": $nsdList,
            "y": ${results2}
          }""".trimIndent()
        )
      }
    }
  }
  
  fun rp2Cdf_nsd() {
    val (d, n, p, q, k, nsd) = referenceSetting
    var max1 = 0
    val result1 =
      nsdList.sorted().map { nsd ->
        var s = 0
        val sum = ReducibleLazyEvaluation<Int, Int>({ 0 })
        
        val y = topoRange.flatMap { topoIdx ->
          parseLog("dist/" + id(n, topoIdx, q, k, p, d, nsd, "Online") + ".txt")
        }.flatMap { it.majorPaths.flatMap { it.recoveryPaths.map { it.path.size - 1 } } }
        
        y.groupBy { it }.map { (k, v) -> k to v.size }.sortedBy { it.first }.forEach { (k, v) ->
          max1 = Math.max(max1, k)
          s += v
          (k..1000).forEach { i -> sum[i] = s }
        }
        
        (0..max1).map { sum[it] / s.toDouble() }
      }
    
    children.add(
      """
        {
          "markerSize": 0,
          "name": "${"a2-rp-len-cdf-$d-$n-$p-$q-$k-nsd".replace(".", "")}",
          "solutionList": ${nsdList.sorted().map { """ "$it S-D pair${if (it > 1) "s" else ""}" """ }},
          "legendColumn": 2,
          "legendFontSize": 10,
          "legendAutomaticallyReorder": false,
          "xTitle": "Length of recovery path",
          "yTitle": "CDF",
          "xLimit": [0, $max1],
          "yLimit": [0, 1],
          "x": ${(0..max1).toList()},
          "y": ${result1.map { l -> if (l.size < max1 + 1) l + List(max1 + 1 - l.size, { 1.0 }) else l }}
        }""".trimIndent()
    )
    
    var max2 = 0
    val result2 =
      nsdList.sorted().map { nsd ->
        var s = 0
        val sum = ReducibleLazyEvaluation<Int, Int>({ 0 })
        
        val y = topoRange.flatMap { topoIdx ->
          parseLog("dist/" + id(n, topoIdx, q, k, p, d, nsd, "Online") + ".txt")
        }.flatMap { it.majorPaths.flatMap { it.recoveryPaths.map { it.width } } }
        
        y.groupBy { it }.map { (k, v) -> k to v.size }.sortedBy { it.first }.forEach { (k, v) ->
          max2 = Math.max(max2, k)
          s += v
          (k..1000).forEach { i -> sum[i] = s }
        }
        
        (0..max2).map { sum[it] / s.toDouble() }
      }
    
    max2 = result2.map { it.indexOfFirst { it > 0.99 } }.max()!!
    children.add(
      """
        {
          "markerSize": 0,
          "name": "${"a2-rp-wid-cdf-$d-$n-$p-$q-$k-nsd".replace(".", "")}",
          "solutionList": ${nsdList.sorted().map { """ "$it S-D pair${if (it > 1) "s" else ""}" """ }},
          "legendColumn": 2,
          "legendFontSize": 10,
          "legendAutomaticallyReorder": false,
          "xTitle": "Width of recovery path",
          "yTitle": "CDF",
          "xLimit": [0, $max2],
          "yLimit": [0, 1],
          "x": ${(0..max2).toList()},
          "y": ${result2.map { l -> if (l.size < max2 + 1) l + List(max2 + 1 - l.size, { 1.0 }) else l.take(max2 + 1) }}
        }""".trimIndent()
    )
    
    var max3 = 0
    val result3 =
      nsdList.sorted().map { nsd ->
        var s = 0
        val sum = ReducibleLazyEvaluation<Int, Int>({ 0 })
        
        val y = topoRange.flatMap { topoIdx ->
          parseLog("dist/" + id(n, topoIdx, q, k, p, d, nsd, "Online") + ".txt")
        }.flatMap { it.majorPaths.map { it.recoveryPaths.sumBy { it.width } } }
        
        y.groupBy { it }.map { (k, v) -> k to v.size }.sortedBy { it.first }.forEach { (k, v) ->
          max3 = Math.max(max3, k)
          s += v
          (k..1000).forEach { i -> sum[i] = s }
        }
        
        (0..max3).map { sum[it] / s.toDouble() }
      }
    
    children.add(
      """
        {
          "markerSize": 0,
          "name": "${"a2-rp-wid-per-mp-cdf-$d-$n-$p-$q-$k-nsd".replace(".", "")}",
          "solutionList": ${nsdList.sorted().map { """ "$it S-D pair${if (it > 1) "s" else ""}" """ }},
          "legendColumn": 2,
          "legendFontSize": 10,
          "legendAutomaticallyReorder": false,
          "xTitle": "# recovery paths per major path",
          "yTitle": "CDF",
          "xLimit": [0, $max3],
          "yLimit": [0, 1],
          "x": ${(0..max3).toList()},
          "y": ${result3.map { l -> if (l.size < max3 + 1) l + List(max3 + 1 - l.size, { 1.0 }) else l }}
        }""".trimIndent()
    )
    
    var max = 0
    val names = listOf("Online", "Online-R", "CR", "CR-R")
    
    val result = names.map { name ->
      var s = 0
      val sum = ReducibleLazyEvaluation<Int, Int>({ 0 })
      
      topoRange.flatMap { topoIdx ->
        parseLog("dist/" + id(n, topoIdx, q, k, p, d, nsd, name) + ".txt")
      }.map { it.majorPaths.sumBy { it.succ } }.groupBy { it }.map { (k, v) ->
        k to v.size
      }.sortedBy { it.first }.forEach { (k, v) ->
        max = Math.max(max, k)
        s += v
        (k..1000).forEach { i -> sum[i] = s }
      }
      
      (0..max).map { sum[it] / s.toDouble() }
    }
    
    children.add(
      """
      {
        "markerSize": 0,
          "name": "${"rp-throughput-cdf-$d-$n-$p-$q-$k-$nsd".replace(".", "")}",
        "solutionList": ${names.map { """ "${nameMapping[it] ?: it}" """ }},
        "xTitle": "Throughput (eps)",
        "yTitle": "CDF",
        "xLimit": [0, $max],
        "yLimit": [0, 1],
        "x": ${(0..max).toList()},
          "xTicks&Labels": ${(0..max).chunked(5).map { it.first() }},
        "y": ${result.map { l -> if (l.size < max + 1) l + List(max + 1 - l.size, { 1.0 }) else l }}
      }""".trimIndent()
    )
  }
  
  fun rp2Cdf_n() {
    val (d, n, p, q, k, nsd) = referenceSetting
    var max1 = 0
    val result1 =
      nList.sorted().map { n ->
        var s = 0
        val sum = ReducibleLazyEvaluation<Int, Int>({ 0 })
        
        val y = topoRange.flatMap { topoIdx ->
          parseLog("dist/" + id(n, topoIdx, q, k, p, d, nsd, "Online") + ".txt")
        }.flatMap { it.majorPaths.flatMap { it.recoveryPaths.map { it.path.size - 1 } } }
        
        y.groupBy { it }.map { (k, v) -> k to v.size }.sortedBy { it.first }.forEach { (k, v) ->
          max1 = Math.max(max1, k)
          s += v
          (k..1000).forEach { i -> sum[i] = s }
        }
        
        (0..max1).map { sum[it] / s.toDouble() }
      }
    
    children.add(
      """
        {
          "markerSize": 0,
          "name": "${"a2-rp-len-cdf-$d-n-$p-$q-$k-$nsd".replace(".", "")}",
          "solutionList": ${nList.sorted().map { """ "|V| = $it" """ }},
          "xTitle": "Length of recovery path",
          "yTitle": "CDF",
          "xLimit": [0, $max1],
          "yLimit": [0, 1],
          "x": ${(0..max1).toList()},
          "y": ${result1.map { l -> if (l.size < max1 + 1) l + List(max1 + 1 - l.size, { 1.0 }) else l }}
        }""".trimIndent()
    )
    
    var max2 = 0
    val result2 =
      nList.sorted().map { n ->
        var s = 0
        val sum = ReducibleLazyEvaluation<Int, Int>({ 0 })
        
        val y = topoRange.flatMap { topoIdx ->
          parseLog("dist/" + id(n, topoIdx, q, k, p, d, nsd, "Online") + ".txt")
        }.flatMap { it.majorPaths.flatMap { it.recoveryPaths.map { it.width } } }
        
        y.groupBy { it }.map { (k, v) -> k to v.size }.sortedBy { it.first }.forEach { (k, v) ->
          max2 = Math.max(max2, k)
          s += v
          (k..1000).forEach { i -> sum[i] = s }
        }
        
        (0..max2).map { sum[it] / s.toDouble() }
      }
    
    max2 = result2.map { it.indexOfFirst { it > 0.99 } }.max()!!
    
    children.add(
      """
        {
          "markerSize": 0,
          "name": "${"a2-rp-wid-cdf-$d-n-$p-$q-$k-$nsd".replace(".", "")}",
          "solutionList": ${nList.sorted().map { """ "|V| = $it" """ }},
          "xTitle": "Width of recovery path",
          "yTitle": "CDF",
          "xLimit": [0, $max2],
          "yLimit": [0, 1],
          "x": ${(0..max2).toList()},
          "y": ${result2.map { l -> if (l.size < max2 + 1) l + List(max2 + 1 - l.size, { 1.0 }) else l.take(max2 + 1) }}
        }""".trimIndent()
    )
    
    var max3 = 0
    val result3 =
      nList.sorted().map { n ->
        var s = 0
        val sum = ReducibleLazyEvaluation<Int, Int>({ 0 })
        
        val y = topoRange.flatMap { topoIdx ->
          parseLog("dist/" + id(n, topoIdx, q, k, p, d, nsd, "Online") + ".txt")
        }.flatMap { it.majorPaths.map { it.recoveryPaths.sumBy { it.width } } }
        
        y.groupBy { it }.map { (k, v) -> k to v.size }.sortedBy { it.first }.forEach { (k, v) ->
          max3 = Math.max(max3, k)
          s += v
          (k..1000).forEach { i -> sum[i] = s }
        }
        
        (0..max3).map { sum[it] / s.toDouble() }
      }
    
    children.add(
      """
        {
          "markerSize": 0,
          "name": "${"a2-rp-wid-per-mp-cdf-$d-n-$p-$q-$k-$nsd".replace(".", "")}",
          "solutionList": ${nList.sorted().map { """ "|V| = $it" """ }},
          "xTitle": "# recovery paths per major path",
          "yTitle": "CDF",
          "xLimit": [0, $max3],
          "yLimit": [0, 1],
          "x": ${(0..max3).toList()},
          "y": ${result3.map { l -> if (l.size < max3 + 1) l + List(max3 + 1 - l.size, { 1.0 }) else l }}
        }""".trimIndent()
    )
  }
  
  fun rp2Nsd() {
    val (d, n, p, q, k, nsd) = referenceSetting
    val names = listOf("Online", "Online-R")
    
    val results = names.map { name ->
      nsdList.sorted().map { nsd ->
        val rlist = topoRange.flatMap { topoIdx ->
          parseLog("dist/" + id(n, topoIdx, q, k, p, d, nsd, name) + ".txt")
        }
        
        rlist.map { it.majorPaths.sumByDouble { it.succ.toDouble() } }.average()
      }
    }
    
    val results2 = names.map { name ->
      nsdList.sorted().map { nsd ->
        val rlist = topoRange.flatMap { topoIdx ->
          parseLog("dist/" + id(n, topoIdx, q, k, p, d, nsd, name) + ".txt")
        }
        
        rlist.map { it.majorPaths.count { it.succ > 0 }.toDouble() }.average()
      }
    }
    
    children += listOf(
      """
        {
          "name": "${"a2-rp-throughput-$d-$n-$p-$q-$k-nsd".replace(".", "")}",
          "solutionList": ${names.map { """ "${nameMapping[it] ?: it}" """ }},
          "xTitle": "# S-D pairs in one time slot",
          "yTitle": "Throughput (eps)",
          "x": ${nsdList},
          "y": ${results}
        }""".trimIndent(), """
        {
          "name": "${"a2-rp-succ-pairs-$d-$n-$p-$q-$k-nsd".replace(".", "")}",
          "solutionList": ${names.map { """ "${nameMapping[it] ?: it}" """ }},
          "xTitle": "# S-D pairs in one time slot",
          "yTitle": "Success S-D pairs",
          "x": ${nsdList},
          "y": ${results2}
        }""".trimIndent()
    )
  }
  
  fun rp2N() {
    val (d, n, p, q, k, nsd) = referenceSetting
    
    val names = listOf("Online", "Online-R")
    
    val results = names.map { name ->
      nList.sorted().map { n ->
        val rlist = topoRange.flatMap { topoIdx ->
          parseLog("dist/" + id(n, topoIdx, q, k, p, d, nsd, name) + ".txt")
        }
        
        rlist.map { it.majorPaths.sumByDouble { it.succ.toDouble() } }.average()
      }
    }
    
    val results2 = names.map { name ->
      nList.sorted().map { n ->
        val rlist = topoRange.flatMap { topoIdx ->
          parseLog("dist/" + id(n, topoIdx, q, k, p, d, nsd, name) + ".txt")
        }
        
        rlist.map { it.majorPaths.count { it.succ > 0 }.toDouble() }.average()
      }
    }
    
    children += listOf(
      """
        {
          "name": "${"a2-rp-throughput-$d-n-$p-$q-$k-$nsd".replace(".", "")}",
          "solutionList": ${names.map { """ "${nameMapping[it] ?: it}" """ }},
          "xTitle": "|V|",
          "xLog" : true,
          "xTicks&Labels": [${nList.sorted()}, ${nList.sorted().map { """ "$it" """ }}],
          "yTitle": "Throughput (eps)",
          "x": ${nList.sorted()},
          "y": ${results}
        }""".trimIndent(), """
        {
          "name": "${"a2-rp-succ-pairs-$d-n-$p-$q-$k-$nsd".replace(".", "")}",
          "solutionList": ${names.map { """ "${nameMapping[it] ?: it}" """ }},
          "xTitle": "|V|",
          "xLog" : true,
          "xTicks&Labels": [${nList.sorted()}, ${nList.sorted().map { """ "$it" """ }}],
          "yTitle": "Success S-D pairs",
          "x": ${nList.sorted()},
          "y": ${results2}
        }""".trimIndent()
    )
  }
  
  companion object {
    @JvmStatic
    fun main(args: Array<String>) {
      val p = Plot()
      
      p.throughputCdf()
      
      p.throughputD()
      p.throughputK()
      p.throughputN()
      p.throughputNsd()
      p.throughputP()
      p.throughputQ()
      
      p.efficiency()
      p.fairness()
      p.recovery1()
      
      p.rp2Cdf_nsd()
      p.rp2Cdf_n()
      p.rp2N()
      p.rp2Nsd()
      
      p.plot()
    }
  }
}

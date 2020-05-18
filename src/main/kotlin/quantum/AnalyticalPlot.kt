package quantum

import utils.also
import java.io.File
import kotlin.math.pow

class AnalyticalPlot {
  fun parallel() {
    val q = 0.95
    val n = 10
    val Q = q.pow(n - 1)
    File("../plot/last-plot-data.json").writeText(
      """
{
  'type': "line",
  'figWidth': 600,
  'figHeight': 350,
  'usetex': False,
  'mainColors': ['#0072bc',
                 '#d85119',
                 '#edb021',
                 '#7a8cbf',
                 '#009d70',
                 '#979797',
                 '#53b2ea'],

  'legendLoc': 'best',
  'legendColumn': 1,

  'markerSize': 8,
  'lineWidth': 1,

  'xLog': False,
  'yLog': False,
  'xGrid': True,
  'yGrid': True,

  'xFontSize': 24,
  'xTickRotate': False,
  'yFontSize': 24,
  'legendFontSize': 18,
  'output': True,

  'children': [
    {
      'markerSize': 0,
      'name': 'E-p-2',
      'solutionList': ('Separate', 'Parallel'),
      'figTitle': "",
      'xTitle': 'P(one link success)',
      'yTitle': 'E(\\# entanglements)',
      'x': ${(0..100).map { it / 100.0 }},
      'y': [
        ${(0..100).map { it / 100.0 }.map { p ->
        2 * p * p * q
      }},
        ${(0..100).map { it / 100.0 }.map { p ->
        val t = 1 - p
        2 * p.pow(4) * q + 4 * p * p * t * q
      }}],
    },
    {
      'markerSize': 0,
      'name': 'E-p-10',
      'solutionList': ('Separate', 'Parallel'),
      'figTitle': "",
      'xTitle': 'P(one link success)',
      'yTitle': 'E(\\# entanglements)',
      'x': ${(0..100).map { it / 100.0 }},
      'y': [
        ${(0..100).map { it / 100.0 }.map { p ->
        2 * p.pow(n) * Q
      }},
        ${(0..100).map { it / 100.0 }.map { p ->
        val t = 1 - p
        val pn_1 = (2..n).fold(2 * p * t) { pk_1_1, k ->
          pk_1_1 * (1 - t * t) + p.pow(2 * k - 2) * 2 * p * t
        }
        pn_1 * Q + 2 * p.pow(2 * n) * (Q * Q + (1 - q.pow(n - 1)) * Q)
      }}],
    },
    {
      'markerSize': 0,
      'name': 'E-p-10-3',
      'solutionList': ('-1-1-1-', '-1-2-', '-3-'),
      'figTitle': "",
      'xTitle': 'P(one link success)',
      'yTitle': 'E(\\# entanglements)',
      'x': ${(0..100).map { it / 100.0 }},
      'y': ${listOf(
        (0..100).map { it / 100.0 }.map { p ->
          3 * p.pow(n) * Q
        },
        (0..100).map { it / 100.0 }.map { p ->
          val t = 1 - p
          val pn_1 = (2..n).fold(2 * p * t) { pk_1_1, k ->
            pk_1_1 * (1 - t * t) + p.pow(2 * k - 2) * 2 * p * t
          }
          p.pow(n) * Q + pn_1 * Q + 2 * p.pow(2 * n) * (Q * Q + (1 - Q) * Q)
        },
        (0..100).map { it / 100.0 }.map { p ->
          val t = 1 - p
          val (pn_1, pn_2) = (2..n).fold(3 * p * t * t to 3 * p * p * t) { (pk_1_1, pk_1_2), k ->
            pk_1_1 * (1 - t.pow(3)) + (pk_1_2 + p.pow(3 * k - 3)) * 3 * p * t * t to
              pk_1_2 * (3 * p * p * t + p * p * p) + p.pow(3 * k - 3) * 3 * p * p * t
          }
          val pn_3 = p.pow(3 * n)
          
          pn_1 * Q + 2 * pn_2 * Q * Q + 3 * pn_3 * Q * Q * Q +
            2 * 3 * pn_3 * Q * Q * (1 - Q) + 3 * pn_3 * Q * (1 - Q).pow(2) +
            2 * pn_2 * Q * (1 - Q)
        }
      )},
    },
    {
      'markerSize': 0,
      'name': 'P-p-10',
      'solutionList': ('1 path', '2 paths', '3 paths', 'paths exist'),
      'figTitle': "",
      'xTitle': 'P(one link success)',
      'yTitle': 'P',
      'x': ${(0..100).map { it / 100.0 }},
      'y': ${run {
        val tmp = (0..100).map { it / 100.0 }.map { p ->
          val t = 1 - p
          val (pn_1, pn_2) = (2..n).fold(3 * p * t * t to 3 * p * p * t) { (pk_1_1, pk_1_2), k ->
            pk_1_1 * (1 - t.pow(3)) + (pk_1_2 + p.pow(3 * k - 3)) * 3 * p * t * t to
              pk_1_2 * (3 * p * p * t + p * p * p) + p.pow(3 * k - 3) * 3 * p * p * t
          }
          
          pn_1 to pn_2 also p.pow(3 * n)
        }
        
        listOf(tmp.map { it.first }, tmp.map { it.second }, tmp.map { it.third }, tmp.map { it.toList().sumByDouble { it } })
      }},
    },
    ${listOf(0.6, 0.8, 0.9).map { p ->
        val t = 1 - p
        """{
        'markerSize': 8,
        'name': 'E-hops-${p.toString().replace(".", "")}',
        'solutionList': ('1-path', '2-path', '3-path'),
        'figTitle': "",
        'xTitle': 'Number of hops',
        'yTitle': 'EXT',
        'x': ${(1..10).toList()},
        'y': ${run {
          val e1 = (1..10).map { n -> p.pow(n) * Q }
          
          val e2 = (1..10).map { n ->
            val pn_1 = (2..n).fold(2 * p * t) { pk_1_1, k ->
              pk_1_1 * (1 - t * t) + p.pow(2 * k - 2) * 2 * p * t
            }
            
            p.pow(n) * Q + pn_1 * Q + 2 * p.pow(2 * n) * (Q * Q + (1 - Q) * Q)
          }
          
          val e3 = (1..10).map { n ->
            val Q = q.pow(n - 1)
            val (pn_1, pn_2) = (2..n).fold(3 * p * t * t to 3 * p * p * t) { (pk_1_1, pk_1_2), k ->
              pk_1_1 * (1 - t.pow(3)) + (pk_1_2 + p.pow(3 * k - 3)) * 3 * p * t * t to
                pk_1_2 * (3 * p * p * t + p * p * p) + p.pow(3 * k - 3) * 3 * p * p * t
            }
            val pn_3 = p.pow(3 * n)
            
            pn_1 * Q + 2 * pn_2 * Q * Q + 3 * pn_3 * Q * Q * Q +
              2 * 3 * pn_3 * Q * Q * (1 - Q) + 3 * pn_3 * Q * (1 - Q).pow(2) +
              2 * pn_2 * Q * (1 - Q)
          }
          
          listOf(e1, e2, e3)
        }},
      }"""
      }.joinToString()
      }
  ]
}
""")
  }
  
  companion object {
    @JvmStatic
    fun main(args: Array<String>) {
      AnalyticalPlot().parallel()
    }
  }
}

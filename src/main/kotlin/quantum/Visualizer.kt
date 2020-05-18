package quantum

import quantum.algorithm.Algorithm
import quantum.topo.Topo
import utils.*
import java.awt.*
import java.awt.Font
import java.awt.event.*
import java.awt.geom.AffineTransform
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.lang.Math.max
import java.lang.Math.min
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.Timer
import quantum.topo.*

class MyCanvas(val algorithm: Algorithm) : JPanel() {
  val topo: Topo = algorithm.topo
  val nodeDiameter = 20.0
  val nodeRadius = nodeDiameter / 2
  
  var zoom = 1.0
  
  var offset = 0.0 to 0.0
  var offsetBeforeDrag = 0.0 to 0.0
  
  /** starting location of a drag  */
  var startX = -1
  var startY = -1
  
  /** current location of a drag  */
  var curX = -1
  var curY = -1
  
  // And two methods from MouseMotionListener:
  init {
    preferredSize = Dimension(640, 640)
    background = Color.white
    
    addMouseMotionListener(object : MouseMotionAdapter() {
      override fun mouseDragged(e: MouseEvent?) {
        val p = e!!.point
        curX = p.x
        curY = p.y
        offset = offsetBeforeDrag.first + (curX - startX) to offsetBeforeDrag.second + (curY - startY)
      }
    })
    
    addMouseWheelListener(object : MouseWheelListener {
      override fun mouseWheelMoved(e: MouseWheelEvent?) {
        val notches = e?.getWheelRotation() ?: 0
        if (notches < 0) {
          zoom *= 1.1
        } else {
          zoom /= 1.1
        }
        
        zoom = min(10.0, max(0.1, zoom))
      }
    })
    
    addMouseListener(object : MouseAdapter() {
      override fun mousePressed(e: MouseEvent?) {
        if (e!!.clickCount == 1) {
          val p = e.point
          startX = p.x
          startY = p.y
          offsetBeforeDrag = offset
        }
      }
    })
    
    addKeyListener(object : KeyAdapter() {
      override fun keyTyped(p0: KeyEvent?) {
        if (p0!!.keyChar == 'r') {
          zoom = 1.0
          offset = 0.0 to 0.0
        }
      }
    })
    
    Timer(50) { e: ActionEvent ->
      repaint()
    }.start()
  }
  
  fun project(coordinates: DoubleArray): Pair<Double, Double> = coordinates[0] to coordinates[1]
  
  internal fun drawTopo(g: Graphics2D) = try {
    var bottomLeft = Double.MAX_VALUE to Double.MAX_VALUE
    var topRight = Double.MIN_VALUE to Double.MIN_VALUE
    
    fun updateBorder(x: Double, y: Double) {
      bottomLeft = min(x, bottomLeft.first) to min(y, bottomLeft.second)
      topRight = max(x, topRight.first) to max(y, topRight.second)
    }
    
    fun updateBorder(xy: Pair<Double, Double>) {
      val (x, y) = xy
      updateBorder(x, y)
    }
    
    topo.nodes.forEach {
      updateBorder(project(it.loc))
    }
    
    val margin = 20
    
    val xscale = (width - 2 * margin) / (topRight.first - bottomLeft.first)
    val yscale = (height - 2 * margin) / (topRight.second - bottomLeft.second)
    
    val scale = min(xscale, yscale)
    
    fun place(x: Double, y: Double): Pair<Double, Double> {
      return (x - bottomLeft.first) * scale + margin to (y - bottomLeft.second) * scale + margin
    }
    
    fun place(xy: Pair<Double, Double>): Pair<Double, Double> {
      val (x, y) = xy
      return place(x, y)
    }
    
    fun place(coor: DoubleArray) = place(project(coor))
    
    g.font = Font("serif", Font.PLAIN, 10)
    g.color = Color.black
    topo.nodes.forEach { n ->
      val (x, y) = place(n.loc)
      g.drawString("${n.remainingQubits}/${n.nQubits}", (x - nodeRadius).toFloat(), (y - nodeRadius).toFloat())
      g.drawString("${n.id.padTo(3)}", (x - 9).toFloat(), (y + 3).toFloat())
      g.color = if (algorithm.srcDstPairs.any { it.first == n }) Color.MAGENTA else if (algorithm.srcDstPairs.any { it.second == n }) Color.BLUE else Color.BLACK
      g.draw(Ellipse2D.Double(x - nodeRadius, y - nodeRadius, nodeDiameter, nodeDiameter))
      if (algorithm.srcDstPairs.any { it.contains(n) })
        g.draw(Ellipse2D.Double(x - nodeRadius - 2, y - nodeRadius - 2, nodeDiameter + 4, nodeDiameter + 4))
      g.color = Color.BLACK
    }
    
    topo.links.groupBy { it.n1 to it.n2 }.map { it.value.count { it.entangled } to it.value.count { it.assigned } also it.value.size to it.key }.forEach { (count, it) ->
      val xy1 = place(it.n1.loc).toList().toDoubleArray()
      val xy2 = place(it.n2.loc).toList().toDoubleArray()
      
      val _1to2 = (xy2 - xy1) / +(xy2 - xy1) * (nodeRadius)
      val (x1, y1) = xy1 + _1to2
      val (x2, y2) = xy2 - _1to2
      
      g.color = Color.black
      val middle = (xy2 + xy1) * 0.5
      
      val (succ, assigned, capacity) = count
      
      if (assigned != 0)
        g.drawString("[${succ}/${assigned}/${capacity}]", middle[0].toFloat(), middle[1].toFloat())
      
      g.color = if (count.first > 0) Color.GREEN else Color.RED
      g.draw(Line2D.Double(x1, y1, x2, y2))
    }
  } catch (e: Throwable) {
  }
  
  public override fun paintComponent(gg: Graphics) {
    super.paintComponent(gg)
    val g = gg as Graphics2D
    
    val at = AffineTransform()
    at.translate(offset.first, offset.second)
    at.scale(zoom, zoom)
    
    g.setTransform(at)
    
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    
    drawTopo(g)
  }
}

class Visualizer(algorithm: Algorithm) : JFrame() {
  var clicked = true
  
  fun requireClick() {
    clicked = false
    
    while (!clicked) {
      Thread.sleep(1000)
    }
  }
  
  init {
    this.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    this.title = "Routing Quantum Entanglement"
    this.isResizable = true

//    val toolBar = JToolBar("Still draggable")
//    toolBar.setFloatable(false)
//    toolBar.setRollover(true)
//    val button = JButton()
//    button.text = "test"
//    toolBar.add(button)
//    this.add(toolBar, BorderLayout.PAGE_START)
    
    val myCanvas = MyCanvas(algorithm)
    myCanvas.addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(p0: MouseEvent?) {
        if (p0?.getClickCount() == 2)
          clicked = true
      }
    })
    
    myCanvas.isFocusable = true
    
    this.add(myCanvas, BorderLayout.CENTER)
    this.pack()
    this.setLocationRelativeTo(null)
    this.isVisible = true
  }
}

//fun main() {
////  Visualizer(Topo(File("input/test-topo.txt").readText()))
////  Visualizer(Topo.generate())
//
////  val topo = Topo(File("input/test-topo.txt").readText())
//  val topo = Topo.generate()
//
//  Thread {
//    val solver = ProposedAlgorithm(topo)
//    println(topo.getStatistics())
//
//    solver.prepare()
//
//    (1..10).map { numPairs ->
////      (1..100).forEach { println(solver.work(numPairs)) }
//    }
//  }.start()
//
//  Visualizer(topo)
//}

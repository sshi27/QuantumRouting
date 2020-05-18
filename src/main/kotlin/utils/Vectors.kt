package utils

/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

infix fun List<Double>.dot(l: List<Double>): Double = this.zip(l).foldRight(0.0, { p, acc ->
  acc + p.first * p.second
})

operator fun Double.times(array: DoubleArray) = array.map { this * it }.toDoubleArray()
operator fun DoubleArray.plus(another: DoubleArray) = this.zip(another).map { it.first + it.second }.toDoubleArray()
operator fun DoubleArray.minus(another: DoubleArray) = this + -another
operator fun DoubleArray.times(another: DoubleArray) = this.zip(another).fold(0.0, { acc, pair -> acc + pair.first * pair.second })
operator fun DoubleArray.times(scale: Double) = scale * this
operator fun DoubleArray.div(scale: Double) = 1 / scale * this
operator fun DoubleArray.unaryPlus() = Math.sqrt(this * this)
operator fun DoubleArray.unaryMinus() = this.map { -it }.toDoubleArray()
operator fun DoubleArray.plusAssign(another: DoubleArray): Unit = this.forEachIndexed({ index, d -> this[index] += another[index] })
fun DoubleArray.normalize() = this / +this
fun cos(one: DoubleArray, other: DoubleArray) = one * other / (+one * +other)

infix fun DoubleArray.outOf(bound: DoubleArray) = this.foldIndexed(false, { index, b, v -> b || v < bound[index * 2] || v > bound[index * 2 + 1] })

infix fun DoubleArray.crossProduct(other: DoubleArray): DoubleArray {
  assert(this.size == 3 && other.size == 3)
  
  return doubleArrayOf(
    this[1] * other[2] - this[2] * other[1],
    this[2] * other[0] - this[0] * other[2],
    this[0] * other[1] - this[1] * other[0]
  )
}

fun DoubleArray.rotate(angle: Double, axis: DoubleArray): DoubleArray {  // this vector is 3D. move one step forward 2D
  return this.rotate(angle, doubleArrayOf(axis[0], axis[1], 1.0), doubleArrayOf(axis[0], axis[1], this[2]))
}

fun DoubleArray.rotate(angle: Double, axis: DoubleArray, base: DoubleArray): DoubleArray {  // move one step forward 3D
  val a = this - base
  val b = axis.normalize()
  
  // a = a1 + a2, where (a1 || b) and (a2 any|any b)
  val an = (a * b) * b
  val ao = a - an
  
  // a2 and a2p is the scaled orthonormal basis of the plane any|any b
  // a2r is a2 rotated around b:
  val a2p = b crossProduct a
  val a2r = ao * Math.cos(angle) + a2p * Math.sin(angle)
  
  val result = base + an + a2r
  this.mapIndexed({ index, _ -> this[index] = result[index] })
  
  return this
}                     // NOTE: this will cause the DIM to be exactly 3

data class Vector2(val x: Double, val y: Double) {
  val length get() = Math.sqrt(x * x + y * y)
  
  fun normalized(): Vector2 {
    val len = length
    return Vector2(x / len, y / len)
  }
  
  fun copyCoordinatesTo(arr: MutableList<Double>) {
    arr.add(x)
    arr.add(y)
  }
  
  operator fun minus(other: Vector2) = Vector2(x - other.x, y - other.y)
  operator fun plus(other: Vector2) = Vector2(x + other.x, y + other.y)
  operator fun times(other: Double) = Vector2(x * other, y * other)
  operator fun div(other: Double) = Vector2(x / other, y / other)
  
  companion object {
    val Zero = Vector2(0.0, 0.0)
  }
}

data class Vector3(val x: Double, val y: Double, val z: Double) {
  constructor(vector2: Vector2, z: Double) : this(vector2.x, vector2.y, z) {}
  
  val length get() = Math.sqrt(x * x + y * y + z * z)
  
  fun crossProduct(other: Vector3): Vector3 =
    Vector3(y * other.z - z * other.y, z * other.x - x * other.z, x * other.y - y * other.x)
  
  fun dotProduct(other: Vector3) = (x * other.x + y * other.y + z * other.z)
  
  fun normalized(): Vector3 {
    val len = length
    return Vector3(x / len, y / len, z / len)
  }
  
  fun copyCoordinatesTo(arr: MutableList<Double>) {
    arr.add(x)
    arr.add(y)
    arr.add(z)
  }
  
  operator fun minus(other: Vector3) = Vector3(x - other.x, y - other.y, z - other.z)
  operator fun plus(other: Vector3) = Vector3(x + other.x, y + other.y, z + other.z)
  operator fun times(other: Double) = Vector3(x * other, y * other, z * other)
}

data class Vector4(val x: Double, val y: Double, val z: Double, val w: Double) {
  constructor(vector3: Vector3, w: Double) : this(vector3.x, vector3.y, vector3.z, w)
  
  fun copyCoordinatesTo(arr: MutableList<Double>) {
    arr.add(x)
    arr.add(y)
    arr.add(z)
    arr.add(w)
  }
}

data class Matrix3(val col1: Vector3, val col2: Vector3, val col3: Vector3) {
  operator fun times(other: Vector3) = Vector3(
    col1.x * other.x + col2.x * other.y + col3.x * other.z,
    col1.y * other.x + col2.y * other.y + col3.y * other.z,
    col1.z * other.x + col2.z * other.y + col3.z * other.z
  )
  
  operator fun times(other: Matrix3) = Matrix3(
    col1 = this * other.col1,
    col2 = this * other.col2,
    col3 = this * other.col3
  )
}

fun Vector3.rotate(axis: Vector3, angle: Double): Vector3 {
  val a = this
  val b = axis.normalized()
  val a1 = b * a.dotProduct(b)
  val a2 = a - a1
  
  // a = a1 + a2, where (a1 || b) and (a2 any|any b)
  
  val a2p = b.crossProduct(a2)
  
  // a2 and a2p is the scaled orthonormal basis of the plane any|any b
  
  // a2r is a2 rotated around b:
  val a2r = a2 * Math.cos(angle) + a2p * Math.sin(angle)
  
  return a1 + a2r
}

fun rotationMatrix(axis: Vector3, angle: Double): Matrix3 {
  // Take into account that
  //   M = M * I = (M * e1 | M * e2 | M * e3)
  
  val e1 = Vector3(1.0, 0.0, 0.0)
  val e2 = Vector3(0.0, 1.0, 0.0)
  val e3 = Vector3(0.0, 0.0, 1.0)
  
  return Matrix3(
    col1 = e1.rotate(axis, angle),
    col2 = e2.rotate(axis, angle),
    col3 = e3.rotate(axis, angle)
  )
}

fun diagonalMatrix(d1: Double, d2: Double, d3: Double) = Matrix3(
  col1 = Vector3(d1, 0.0, 0.0),
  col2 = Vector3(0.0, d2, 0.0),
  col3 = Vector3(0.0, 0.0, d3)
)

data class Matrix4(val col1: Vector4, val col2: Vector4, val col3: Vector4, val col4: Vector4) {
  constructor(matrix3: Matrix3, col4: Vector4 = Vector4(0.0, 0.0, 0.0, 1.0)) : this(
    col1 = Vector4(matrix3.col1, 0.0),
    col2 = Vector4(matrix3.col2, 0.0),
    col3 = Vector4(matrix3.col3, 0.0),
    col4 = col4
  )
  
  fun flatten(): DoubleArray {
    val values = mutableListOf<Double>()
    col1.copyCoordinatesTo(values)
    col2.copyCoordinatesTo(values)
    col3.copyCoordinatesTo(values)
    col4.copyCoordinatesTo(values)
    return values.toDoubleArray()
  }
  
  operator fun times(other: Vector4) = Vector4(
    col1.x * other.x + col2.x * other.y + col3.x * other.z + col4.x * other.w,
    col1.y * other.x + col2.y * other.y + col3.y * other.z + col4.y * other.w,
    col1.z * other.x + col2.z * other.y + col3.z * other.z + col4.z * other.w,
    col1.w * other.x + col2.w * other.y + col3.w * other.z + col4.w * other.w
  )
  
  operator fun times(other: Matrix4) = Matrix4(
    col1 = this * other.col1,
    col2 = this * other.col2,
    col3 = this * other.col3,
    col4 = this * other.col4
  )
}

/**
 * The matrix to perform the orthographic projection from the world coordinate system to the clip space,
 * as described in [glOrtho documentation](https://www.khronos.org/registry/OpenGL-Refpages/es1.1/xhtml/glOrtho.xml)
 */
fun orthographicProjectionMatrix(
  left: Double, right: Double,
  bottom: Double, top: Double,
  near: Double, far: Double
): Matrix4 {
  val m3 = diagonalMatrix(
    2 / (right - left),
    2 / (top - bottom),
    -2 / (far - near)
  )
  
  val tx = -(right + left) / (right - left)
  val ty = -(top + bottom) / (top - bottom)
  val tz = -(far + near) / (far - near)
  
  return Matrix4(m3, col4 = Vector4(tx, ty, tz, 1.0))
}

fun translationMatrix(dx: Double, dy: Double, dz: Double) = Matrix4(
  matrix3 = diagonalMatrix(1.0, 1.0, 1.0),
  col4 = Vector4(dx, dy, dz, 1.0)
)

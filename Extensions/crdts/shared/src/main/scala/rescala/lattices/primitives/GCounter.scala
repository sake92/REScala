package rescala.lattices.primitives

import rescala.lattices.{IdUtil, Lattice}

import scala.collection.immutable.HashMap

case class GCounter(id: IdUtil.Id, payload: HashMap[IdUtil.Id, Int]) {
  def value: Int = payload.values.sum

  def fromValue(value: Int): GCounter = GCounter(id, HashMap(id -> value))

  def fromPayload(payload: HashMap[IdUtil.Id, Int]): GCounter = GCounter(id, payload)

  def increase = GCounter(id, payload + (id -> (payload(id) + 1)))
}

object GCounter {
  def apply(value: Int): GCounter = {
    val id = IdUtil.genId // assign random id based on host
    GCounter(id, HashMap(id -> value))
  }

  implicit def GCounterCRDT: Lattice[GCounter] = new Lattice[GCounter] {
    override def merge(left: GCounter, right: GCounter): GCounter =
      GCounter(left.id,
        left.payload.merged(right.payload) {
          case ((k, v1), (_, v2)) => (k, v1 max v2)
        })
  }

}
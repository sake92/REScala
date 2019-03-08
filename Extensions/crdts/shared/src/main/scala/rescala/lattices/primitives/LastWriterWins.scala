package rescala.lattices.primitives

import rescala.lattices.Lattice

case class LastWriterWins[A](timestamp: Long, payload: A) {
  def map[B](f: A => B): LastWriterWins[B] = LastWriterWins(f(payload))
}

object LastWriterWins {
  def apply[A](value: A): LastWriterWins[A] = {
    val timestamp = System.currentTimeMillis()
    LastWriterWins(timestamp, value)
  }

  implicit def LastWriterWinsCRDT[A]: Lattice[LastWriterWins[A]] = new Lattice[LastWriterWins[A]] {
    override def merge(left: LastWriterWins[A], right: LastWriterWins[A]): LastWriterWins[A] =
      if (right.timestamp > left.timestamp) right else left
  }

}
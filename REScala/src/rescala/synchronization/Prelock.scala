package rescala.synchronization

import rescala.graph.Reactive
import rescala.propagation.{ LevelQueue, TurnImpl }
import rescala.turns.{ Engines, Turn, Engine }

trait Prelock extends TurnImpl with InterturnDependencyChanges {

  final val key: Key = new Key(this)

  /**
   * creating a signal causes some unpredictable reactives to be used inside the turn.
   * these will have their locks be acquired dynamically see below for how that works.
   * the newly created reactive on the other hand can not be locked by anything, so we just grab the lock
   * (we do need to grab it, so it can be transferred to some other waiting transaction).
   * it is important, that the locks for the dependencies are acquired BEFORE the constructor for the new reactive.
   * is executed, because the constructor typically accesses the dependencies to create its initial value.
   */
  override def create[T <: Reactive](dependencies: Set[Reactive])(f: => T): T = {
    dependencies.foreach(_.lock.acquireDynamic(key))
    val reactive = f
    reactive.lock.lock(key)
    super.create(dependencies)(reactive)
  }

  /** similar to create, except for the ensure level and evaluate calls */
  override def createDynamic[T <: Reactive](dependencies: Set[Reactive])(f: => T): T = {
    dependencies.foreach(_.lock.acquireDynamic(key))
    val reactive = f
    reactive.lock.lock(key)
    super.createDynamic(dependencies)(reactive)
  }

  /** this is called after the turn has finished propagating, but before handlers are executed */
  override def realeasePhase(): Unit = {
//    println(key + ": Release Phase")
    key.releaseAll()
//    println(key + ": Release Phase End")
  }
  override def propagationPhase(): Unit = {
//    println(key + ": Propagation Phase")
    super.propagationPhase()
//    println(key + ": Propagation Phase End")
  }

  /** allow turn to handle dynamic access to reactives */
  override def accessDynamic(dependency: Reactive): Unit = dependency.lock.acquireDynamic(key)
}

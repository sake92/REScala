package rescala.twoversion

import rescala.engine.TurnImpl
import rescala.graph.{Pulsing, Reactive, ReevaluationResult}

import scala.util.control.NonFatal

/**
  * Basic implementation of the most fundamental propagation steps as defined by AbstractPropagation.
  * Only compatible with spore definitions that store a pulse value and support graph operations.
  *
  * @tparam S Struct type that defines the spore type used to manage the reactive evaluation
  */
trait TwoVersionPropagationImpl[S <: TwoVersionStruct] extends TwoVersionPropagation[S] with TurnImpl[S] {
  outer =>

  val token: Token = Token()

  private val toCommit = scala.collection.mutable.ArrayBuffer[Committable[S]]()
  private val observers = scala.collection.mutable.ArrayBuffer[() => Unit]()

  override def schedule(commitable: Committable[S]): Unit = toCommit += commitable

  override def observe(f: () => Unit): Unit = observers += f

  override def commitPhase(): Unit = {
    val it = toCommit.iterator
    while (it.hasNext) it.next().commit(this)
  }

  override def rollbackPhase(): Unit = {
    val it = toCommit.iterator
    while (it.hasNext) it.next().release(this)
  }

  override def observerPhase(): Unit = {
    val it = observers.iterator
    var failure: Throwable = null
    while (it.hasNext) {
      try {
        it.next().apply()
      }
      catch {
        case NonFatal(e) => failure = e
      }
    }
    // find the first failure and rethrow the contained exception
    // we should probably aggregate all of the exceptions,
    // but this is not the place to invent exception aggregation
    if (failure != null) throw failure
  }

  protected def discover(sink: Reactive[S])(source: Reactive[S]): Unit = source.state.discover(sink)(this)

  protected def drop(sink: Reactive[S])(source: Reactive[S]): Unit = source.state.drop(sink)(this)

  final def applyDiff(head: Reactive[S], res: ReevaluationResult.Dynamic[_, S]): Unit = {
    head.state.updateIncoming(res.indepsAfter)(this)
    res.indepsRemoved foreach drop(head)
    res.indepsAdded foreach discover(head)
  }

  /** allow turn to handle dynamic access to reactives */
  def dynamicDependencyInteraction(dependency: Reactive[S]): Unit

  override private[rescala] def staticBefore[P](reactive: Pulsing[P, S]) = reactive.state.base(token)
  override private[rescala] def staticAfter[P](reactive: Pulsing[P, S]) = reactive.state.get(token)
  override private[rescala] def dynamicBefore[P](reactive: Pulsing[P, S]) = {
    dynamicDependencyInteraction(reactive)
    reactive.state.base(token)
  }
  override private[rescala] def dynamicAfter[P](reactive: Pulsing[P, S]) = {
    // Note: This only synchronizes reactive to be serializable-synchronized, but not glitch-free synchronized.
    // Dynamic reads thus may return glitched values, which the reevaluation handling implemented in subclasses
    // must account for by repeating glitched reevaluations!
    dynamicDependencyInteraction(reactive)
    reactive.state.get(token)
  }
  def writeState[P](pulsing: Reactive[S])(value: pulsing.Value): Unit = if (pulsing.state.write(value, token)) this.schedule(pulsing.state)
}

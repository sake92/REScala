package rescala.signals

import rescala.propagation.{Pulse, EvaluationResult, Turn}

object Var {
  def apply[T](initval: T): Var[T] = new Var(initval)

}

/** A root Reactive value without dependencies which can be set */
class Var[T](initval: T) extends Signal[T] {
  currentValue = initval

  final def update(newValue: T): Unit = set(newValue)
  def set(newValue: T): Unit = Turn.newTurn { turn =>
    planUpdate(newValue)(turn)
    turn.startEvaluation()
  }

  def planUpdate(newValue: T)(implicit turn: Turn): Unit = {
    val p = Pulse.diff(newValue, currentValue)
    if (p.isChange) {
      pulse(p)(turn)
      turn.evaluate(this)
    }
  }

  override protected[rescala] def reevaluate()(implicit turn: Turn): EvaluationResult =
    EvaluationResult.Done(changed = true, dependants)
}

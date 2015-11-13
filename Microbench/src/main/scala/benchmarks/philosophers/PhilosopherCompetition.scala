package benchmarks.philosophers

import java.util
import java.util.concurrent.locks.{ReentrantLock, Lock}
import java.util.concurrent.{ThreadLocalRandom, TimeUnit}

import benchmarks.philosophers.PhilosopherTable.{Seating, Thinking}
import benchmarks.{EngineParam, Workload}
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.{BenchmarkParams, ThreadParams}
import rescala.graph.Spores

import scala.annotation.tailrec

@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
class PhilosopherCompetition[S <: Spores] {

  @Benchmark
  def eat(comp: Competition[S], params: ThreadParams, work: Workload): Unit = {
    val myBlock = comp.blocks(params.getThreadIndex % comp.blocks.length)
    while ( {
      val seating: Seating[S] = myBlock(ThreadLocalRandom.current().nextInt(myBlock.length))
      if (comp.manualLocking) {
        val pos = Array(seating.placeNumber, (seating.placeNumber + 1) % comp.philosophers, (seating.placeNumber + 2) % comp.philosophers)
        util.Arrays.sort(pos)
        val firstLock = comp.locks(pos(0))
        val secondLock = comp.locks(pos(1))
        val thirdLock = comp.locks(pos(2))
        firstLock.lock()
        try {
          secondLock.lock()
          try {
            thirdLock.lock()
            try {
              val res = comp.table.tryEat(seating)
              if (res) seating.philosopher.set(Thinking)(comp.table.engine)
              !res
            }
            finally {thirdLock.unlock()}
          }
          finally {secondLock.unlock()}
        }
        finally {firstLock.unlock()}
      }
      else {
        val res = comp.table.tryEat(seating)
        if (res) seating.philosopher.set(Thinking)(comp.table.engine)
        !res
      }
    }
    ) {}

  }
}


@State(Scope.Benchmark)
class Competition[S <: Spores] {

  @Param(Array("16", "48"))
  var philosophers: Int = _

  @Param(Array("block", "alternating", "random", "third"))
  var layout: String = _

  @Param(Array("static", "dynamic"))
  var tableType: String = _

  var table: PhilosopherTable[S] = _

  var blocks: Array[Array[Seating[S]]] = _

  var manualLocking: Boolean = _
  var locks: Array[Lock] = _

  @Setup
  def setup(params: BenchmarkParams, work: Workload, engineParam: EngineParam[S]) = {
    manualLocking = engineParam.engineName == "unmanaged" && layout != "third"
    if (manualLocking) {
      locks = Array.fill(philosophers)(new ReentrantLock())
    }
    table = tableType match {
      case "static" => new PhilosopherTable(philosophers, work.work)(engineParam.engine)
      case "dynamic" => new DynamicPhilosopherTable(philosophers, work.work)(engineParam.engine)
      case "half" => new HalfDynamicPhilosopherTable(philosophers, work.work)(engineParam.engine)
    }
    blocks = (layout match {
      case "block" =>
        val perThread = table.seatings.size / params.getThreads
        table.seatings.sliding(perThread, perThread)
      case "alternating" => deal(table.seatings.toList, math.min(params.getThreads, philosophers))
      case "third" => deal(table.seatings.sliding(4, 4).map(_.head).toList, params.getThreads)
      case "random" => List(table.seatings)
    }).map(_.toArray).toArray
  }

  @TearDown(Level.Iteration)
  def cleanEating(): Unit = {
    //print(s"actually eaten: ${ table.eaten.get() } measured: ")
    table.eaten.set(0)
    table.seatings.foreach(_.philosopher.set(Thinking)(table.engine))
  }

  final def deal[A](initialDeck: List[A], numberOfHands: Int): List[List[A]] = {
    @tailrec
    def loop(deck: List[A], hands: List[List[A]]): List[List[A]] =
      deck match {
        case Nil => hands
        case card :: rest => loop(rest, hands.tail :+ (card :: hands.head))
      }
    loop(initialDeck, List.fill(numberOfHands)(Nil))
  }

}
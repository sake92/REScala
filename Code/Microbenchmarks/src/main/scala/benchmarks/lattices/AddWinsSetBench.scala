package benchmarks.lattices

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._
import rescala.core.Struct
import rescala.extra.lattices.dotstores.{Context, Dot, IntTree}
import rescala.extra.lattices.{IdUtil, Lattice}
import rescala.extra.lattices.sets.{AddWinsSet, AddWinsSetO}

@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 3, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Fork(3)
@Threads(1)
@State(Scope.Thread)
class AddWinsSetBench[S <: Struct] {

  @Param(Array("0", "1", "10", "100", "1000"))
  var setSize: Int = _

  var rep1Set: AddWinsSet[String]        = _
  var rep1SetPlusOne: AddWinsSet[String] = _
  var rep2Set: AddWinsSet[String]        = _
  val rep1id                             = IdUtil.genId()
  val rep2id                             = IdUtil.genId()
  var rep2Delta: AddWinsSet[String]      = _

  private def makeRep(rep: IdUtil.Id): AddWinsSet[String] = {
    0.until(setSize).foldLeft(AddWinsSet.empty[String]) { case (s, v) => s.add(v.toString + rep, rep) }
  }

  @Setup
  def setup(): Unit = {
    rep1Set = makeRep(rep1id)
    rep2Set = makeRep(rep2id)
    rep2Delta = rep2Set.addΔ("hallo welt", rep2id)
    rep1SetPlusOne = Lattice.merge(rep1Set, rep2Delta)
  }

  @Benchmark
  def create() = makeRep(rep1id)

  @Benchmark
  def addOne() = rep1Set.add("Hallo Welt", rep1id)

  @Benchmark
  def addOneDelta() = rep1Set.addΔ("Hallo Welt", rep1id)

  @Benchmark
  def containsNot() = rep1Set.contains("Hallo Welt")

  @Benchmark
  def containsFirst() = rep1Set.contains("0")

  @Benchmark
  def merge() = Lattice.merge(rep1Set, rep2Set)

  @Benchmark
  def mergeSelf() = Lattice.merge(rep1Set, rep1Set)

  @Benchmark
  def mergeSelfPlusOne() = Lattice.merge(rep1Set, rep1Set)

  @Benchmark
  def mergeDelta() = Lattice.merge(rep1Set, rep2Delta)

  @Benchmark
  def serializeUJson() = {
    import upickle.default._
    import Codecs.awsUJsonCodec
    write(rep1Set)
  }

  @Benchmark
  def serializeCirce() = {
    import io.circe.syntax._
    import Codecs.awsCirceCodec
    rep1Set.asJson.noSpaces
  }

  @Benchmark
  def serializeUJsonDelta() = {
    import upickle.default._
    import Codecs.awsUJsonCodec
    write(rep2Delta)
  }

  @Benchmark
  def serializeCirceDelta() = {
    import io.circe.syntax._
    import Codecs.awsCirceCodec
    rep2Delta.asJson.noSpaces
  }

}

object Codecs {

  import upickle.default._

  implicit val dotUJsonCodec: upickle.default.ReadWriter[Dot]          = upickle.default.macroRW
  implicit val itRangeCodec: upickle.default.ReadWriter[IntTree.Range] = upickle.default.macroRW
  implicit val itTreeCodec: upickle.default.ReadWriter[IntTree.Tree]   = upickle.default.macroRW
  implicit val contextCodec: upickle.default.ReadWriter[Context]       = upickle.default.macroRW

  implicit val awsOUJsonCodec: upickle.default.ReadWriter[AddWinsSetO[String]] = upickle.default.macroRW
  implicit val awsUJsonCodec: upickle.default.ReadWriter[AddWinsSet[String]]   = upickle.default.macroRW

  import io.circe.generic.auto._

  implicit val awsOCirceCodec: io.circe.Encoder[AddWinsSetO[String]] =
    io.circe.generic.semiauto.deriveEncoder: @scala.annotation.nowarn
  implicit val awsCirceCodec: io.circe.Encoder[AddWinsSet[String]] =
    io.circe.generic.semiauto.deriveEncoder: @scala.annotation.nowarn
}

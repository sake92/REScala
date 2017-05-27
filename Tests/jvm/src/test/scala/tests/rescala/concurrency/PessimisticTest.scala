package tests.rescala.concurrency

import java.util.concurrent.{CountDownLatch, TimeUnit}

import rescala.Engines
import rescala.fullmv.FullMVEngine
import rescala.parrp.{Backoff, ParRP}
import rescala.testhelper.{ParRPTestTooling, ReevaluationTracker, SetAndExtractTransactionHandle, SynchronizedReevaluation}
import rescala.twoversion.TwoVersionEngineImpl
import tests.rescala.RETests

class PessimisticTest extends RETests {
  engines(Engines.parrp, FullMVEngine)("SynchronizedReevaluation should synchronize reevaluations"){ engine =>
    import engine._

    val v1 = Var(false)
    val v2 = Var(false)
    val (sync1, s1) = SynchronizedReevaluation(v1)
    val (sync2, s2) = SynchronizedReevaluation(v2)
    val trackS1 = new ReevaluationTracker(s1)
    val reached1 = SynchronizedReevaluation.notifyOnceReached(sync1)
    val autoSync = SynchronizedReevaluation.autoSyncNextReevaluation(sync1, sync2)

    val t1 = Spawn{ v1.set(true) }

    assert(reached1.await(1000, TimeUnit.MILLISECONDS))
    trackS1.assertClear(false) // turn did not finish yet
    assert(autoSync.getCount === 1) // but has reduced latch to only second turn missing

    v2.set(true)
    t1.join(1000)
    assert(autoSync.getCount == 0)

    assert(s1.now === true)
    trackS1.assertClear(true)
    assert(s2.now === true)
  }

  engines(Engines.parrp, FullMVEngine)("Pessimistic Engines should safely execute concurrently admitted updates to summed signals"){ engine =>
    import engine._

    val size = 10
    val sources = List.fill(size)(Var(0))

    val sum = sources.map(_.map(identity)).reduce(Signals.lift(_, _)(_ + _))
    val sumTracker = new ReevaluationTracker(sum)

    val latch = new CountDownLatch(size)
    val threads = sources.map(v => Spawn {
      latch.countDown()
      latch.await()
      v.set(1)
    })

    val timeout = System.currentTimeMillis() + 1000
    threads.foreach(_.join(math.max(0, timeout - System.currentTimeMillis())))
    assert(latch.getCount == 0)

    sumTracker.assert((0 to size).reverse:_*)
    assert(sum.now === size)
  }

  // This deadlocks for ParRP, because it blocks turns from starting to run concurrently if their regions intersect beforehand
  engines(FullMVEngine)("safely execute concurrently running updates on summed signals"){ engine =>
    import engine._

    val size = 10
    val sources = List.fill(size)(Var(0))

    val (syncs, maps) = sources.map(SynchronizedReevaluation(_)).unzip
    val latch = SynchronizedReevaluation.autoSyncNextReevaluation(syncs:_*)

    val sum = maps.reduce(Signals.lift(_, _)(_ + _))
    val sumTracker = new ReevaluationTracker(sum)

    val threads = sources.map(v => Spawn {v.set(1)})
    threads.foreach(_.join(1000))
    assert(latch.getCount == 0)

    sumTracker.assert((0 to size).reverse:_*)
    assert(sum.now === size)
  }

  engines(Engines.parrp, FullMVEngine)("Pessimistic Engines should correctly execute crossed dynamic discoveries"){ engine =>
    import engine._

    val v1 = Var(0)
    val v2 = Var(1)

    val (syncIn1, s11) = SynchronizedReevaluation(v1)
    val (syncIn2, s21) = SynchronizedReevaluation(v2)

    // so if s11 becomes true, this adds a dependency on v2
    val s12 = engine.dynamic(s11) { t => if (t.depend(s11) != 0) t.depend(s21) else 2 }

    // this does as above, causing one or the other to access something which will change later
    val s22 = engine.dynamic(s21) { t => if (t.depend(s21) != 1) t.depend(s11) else 3 }

    val results1 = new ReevaluationTracker(s12)
    val results2 = new ReevaluationTracker(s22)

    results1.assertClear(2)
    results2.assertClear(3)

    // force both turns to start executing before either may perform its dynamic discoveries
    val latch = SynchronizedReevaluation.autoSyncNextReevaluation(syncIn1, syncIn2)

    // further force the first turn to wait for manual approval, so that the second turn will execute its discovery first
    val latch1 = SynchronizedReevaluation.manuallySyncNextReevaluation(syncIn1)

    val t1 = Spawn(v1.set(4))
    val t2 = Spawn(v2.set(5))

    assert(latch.await(1000, TimeUnit.MILLISECONDS))
    assert(latch1.getCount === 1) // t1 in should wait for manual approval only
    results2.assertClear() // i should not have propagated over the dynamic discovery, despite being blocked manually

    latch1.countDown()
    t1.join(1000)
    t2.join(1000)

    // turn 1 used the old value of v2 and the retrofitted reevaluation for turn 2 executed and used the new value
    results1.assertClear(5, 1)
    results2.assertClear(4)
  }

  test("ParRP should (not?) Add And Remove Dependency In One Turn") {
    implicit val engine = Engines.parrp
    import engine._

    // this behavior is not necessary for correctness; adding and removing the edge (i.e. regs and unregs +=1)
    // would be equally correct. It is implemented purely to discover accidental behavior changes, but should
    // have its exepected results changed upon intentional behavior changes!
    val b0 = Var(false)
    val b2 = b0.map(identity).map(!_) // dirty hacks to get il_3 to reevaluate first on levelbased engines
    val i0 = Var(11)
    var reeval = 0
    val i1_3 = engine.dynamic(b0) { t => reeval += 1; if (t.depend(b0) && t.depend(b2)) t.depend(i0) else 42 }

    var regs = 0
    var unregs = 0

    val mockFac = new TwoVersionEngineImpl[ParRP, ParRP]("Reg/Unreg counting ParRP",
      new ParRP(new Backoff(), None) {
        override def discover(downstream: Reactive)(upstream: Reactive): Unit = {
          if (upstream eq i0) regs += 1
          super.discover(downstream)(upstream)
        }
        override def drop(downstream: Reactive)(upstream: Reactive): Unit = {
          if (upstream eq i0) unregs += 1
          super.drop(downstream)(upstream)
        }
      })

    import ParRPTestTooling._

    assert(unsafeNow(i1_3) === 42)
    assert(reeval === 1)
    assert(regs === 0)
    assert(unregs === 0)

    // now, this should create some only in turn dynamic changes
    b0.set(true)(mockFac)

    assert(unsafeNow(i1_3) === 42)
    assert(reeval === 3)
    assert(regs === 0)
    assert(unregs === 0)

    // this does not
    b0.set(false)(mockFac)

    assert(unsafeNow(i1_3) === 42)
    assert(reeval === 4)
    assert(regs === 0)
    assert(unregs === 0)

    // this also does not, because the level of the dynamic signals stays on 3
    b0.set(true)(mockFac)

    assert(unsafeNow(i1_3) === 42)
    assert(reeval === 5)
    assert(regs === 0)
    assert(unregs === 0)
  }

  engines(Engines.parrp, FullMVEngine)("Pessimistic Engines should not retrofit a reevaluation for t2, after a dependency might have been added and removed again inside a single t1 While Owned By t2"){ engine =>
    import engine._

    val bl0 = Var(false)
    val (syncB1, bl1) = SynchronizedReevaluation(bl0)
    var bl3: Signal[Boolean] = null // dirty hacks to get b2b3i2 to reevaluate first on non-levelbased engines
    val il0 = Var(11)
    val (syncI1, il1) = SynchronizedReevaluation(il0)

    var reeval = List.empty[Turn]
    // this starts on level 2. when bl0 becomes true bl1 becomes true on level 1
    // at that point both bl1 and bl3 are true which causes il1 to be added as a dependency
    // but then bl3 becomes false at level 3, causing il1 to be removed again
    // after that the level is increased and this nonesense no longer happens
    val b2b3i2 = engine.dynamic(bl1) { t =>
      reeval ::= t.turn
      if (t.depend(bl1)) {
        if (t.depend(bl3)) {
          val res = t.depend(il1)
          assert(res === 11, "did not read old value, this may happen spouriosly, probably because of the timing issue in this test")
          res
        }
        else 37
      }
      else 42
    }
    val results = new ReevaluationTracker(b2b3i2)

    bl3 = bl1.map(identity).map(!_) // dirty hacks to get b2b3i2 to reevaluate first on levelbased engines

    // this is here, so that we have i lock bl1.
    // we need this to be a dynamic lock to lock just this single reactive and not bl3 etc.
    val i2b2 = engine.dynamic(il1)(t => if (t.depend(il1) == 0) t.depend(bl1) else false)
    val results2 = new ReevaluationTracker(i2b2)

    // bl0 -> bl1 -> (bl2) -> bl3
    //           >--------------`--> b2b3i2
    // il0 -> il1  `-> i2b2

    results.assertClear(42)
    assert(reeval.size === 1)
    reeval = List() // drop creation turn
    results2.assertClear(false)

    // require both turns to start executing before either may perform dynamic discoveries
    val latch = SynchronizedReevaluation.autoSyncNextReevaluation(syncB1, syncI1)
    // i has il0, il1, i2b2 locked
    // b has bl0, bl1, bl3, b2b3i1

    // further force the "b" turn to wait for manual approval, so that the second turn will execute its discovery first
    val latch1 = SynchronizedReevaluation.manuallySyncNextReevaluation(syncB1)

    // we start the rescala.turns …
    // i will try to grab bl1, which is locked by b, so i will start to wait on b
    val t1 = Spawn { SetAndExtractTransactionHandle(bl0, true) }
    val t2 = Spawn { SetAndExtractTransactionHandle(il0, 0) }

    assert(latch.await(1000, TimeUnit.MILLISECONDS))
    assert(latch1.getCount === 1) // b should wait for manual approval only
    results2.assertClear() // i should not have propagated over the dynamic discovery, despite not being blocked manually

    latch1.countDown()
    // which causes b to continue and evaluate b2b3i2
    // that will add and remove dependencies on il1, which we have readlocked.
    // that should NOT cause b2b3i2 to be reevaluated when i finally finishes
    val turn1 = t1.join(1000)
    val turn2 = t2.join(1000)

    results.assertClear(37)
    results2.assertClear(true)
    assert(Set(List(turn1, turn1), List(turn1)).contains(reeval), " -- for reference, turn2 was "+turn2)
  }

  engines(Engines.parrp, FullMVEngine)("pessimistic engines should add two dynamic dependencies and remove only one"){ engine =>
    import engine._

    val bl0 = Var(false)
    val (syncB1, bl1) = SynchronizedReevaluation(bl0)
    var bl3: Signal[Boolean] = null // dirty hacks to ensure that b2b3i2 is reevaluated first
    val il0 = Var(11)
    val (syncI1, il1) = SynchronizedReevaluation(il0)

    var reeval = List.empty[Turn]
    // this starts on level 2. when bl0 becomes true bl1 becomes true on level 1
    // at that point both bl1 and bl3 are true which causes il1 and il0 to be added as a dependency
    // but then bl3 becomes false at level 3, causing il1 to be removed again (but il0 is still a dependency)
    // after that the level is increased and this nonesense no longer happens
    val b2b3i2 = engine.dynamic(bl1) { t =>
      reeval ::= t.turn
      if (t.depend(bl1)) {
        if (t.depend(bl3)) {
          val res = t.depend(il0) + t.depend(il1)
          assert(res === 22, "did not read old value")
          res
        }
        else t.depend(il0)
      }
      else 42
    }
    val results = new ReevaluationTracker(b2b3i2)
    bl3 = bl1.map(identity).map(!_) // dirty hacks to get b2b3i2 to reevaluate first on levelbased engines

    // this is here, so that we have i lock bl1.
    // we need this to be a dynamic lock to lock just this single reactive and not bl3 etc.
    val i2b2 = engine.dynamic(il1)(t => if (t.depend(il1) == 17) t.depend(bl1) else false)
    val results2 = new ReevaluationTracker(i2b2)

    // bl0 -> bl1 -> (bl2) -> bl3
    //           >--------------`--> b2b3i2
    // il0 -> il1  `-> i2b2 -> c3

    results.assertClear(42)
    assert(reeval.size === 1)
    reeval = List() // drop creation turn
    results2.assertClear(false)

    // require both turns to start executing before either may perform dynamic discoveries
    val latch = SynchronizedReevaluation.autoSyncNextReevaluation(syncB1, syncI1)
    // i has il0, il1, i2b2 locked
    // b has bl0, bl1, bl3, b2b3i1

    // further force the "b" turn to wait for manual approval, so that the second turn will execute its discovery first
    val latch1 = SynchronizedReevaluation.manuallySyncNextReevaluation(syncB1)

    // we start the rescala.turns …
    // i will try to grab bl1, which is locked by b, so i will start to wait on b
    val t1 = Spawn { SetAndExtractTransactionHandle(bl0, true) }
    val t2 = Spawn { SetAndExtractTransactionHandle(il0, 17)}

    assert(latch.await(1000, TimeUnit.MILLISECONDS))
    assert(latch1.getCount === 1) // b should wait for manual approval only
    results2.assertClear() // i should not have propagated over the dynamic discovery, despite not being blocked manually

    latch1.countDown()
    // which causes b to continue and evaluate b2b3i2
    // that will add a dependencay on i10 and add and remove dependencies on il1, which we have both readlocked.
    // that SHUOLD cause b2b3i2 to be reevaluated when i finally finishes (because the dependency to il0 remains)

    val turn1 = t1.join(1000)
    val turn2 = t2.join(1000)

    results2.assertClear(true)
    results.assertClear(17, 11)
    assert(Set(List(turn2, turn1, turn1), List(turn2, turn1)).contains(reeval))
  }

}

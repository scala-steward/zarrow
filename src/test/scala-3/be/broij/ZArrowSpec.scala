/*Copyright 2024 Julien Broi

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.*/

package be.broij

import be.broij.zarrow._
import zio._
import zio.test._
import zio.test.Assertion._
import zio.test.Gen

object ZArrowSpec extends ZIOSpecDefault:
  def genNonEmptyChunkOf[R, A](genA: Gen[R, A]) =
    Gen.listOf1(genA).map(NonEmptyChunk.fromCons)

  def genAny: Gen[Any, Any] =
    Gen.oneOf(Gen.int, Gen.string, Gen.boolean)

  def genTuple[R1, R2, A1, A2](genFirst: Gen[R1, A1], genSecond: Gen[R2, A2]) =
    genFirst.flatMap(first => genSecond.map((first, _)))

  def genTuple[R, A](gen: Gen[R, A]): Gen[R, (A, A)] =
    genTuple(gen, gen)

  val zio = ZIO.succeed(3)

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("ZArrow")(
      suite(".unit")(
        test("maps any input to Unit") {
          check(genAny) { any =>
            assertZIO(ZArrow.unit(any))(isUnit)
          }
        }
      ),
      suite(".identity")(
        test("maps the input to itself") {
          check(genAny) { any =>
            assertZIO(ZArrow.identity(any))(equalTo(any))
          }
        }
      ),
      suite(".succeed")(
        test("succeeds with the ouput of the provided f: I => R => O") {
          val zArrow = ZArrow.succeed((str: String) => (prefix: String) => prefix + str)
          check(genTuple(Gen.string)) { (str, prefix) =>
            val eff = zArrow(str).provide(ZLayer.succeed(prefix))
            assertZIO(eff)(equalTo(prefix + str))
          }
        },
        test("succeeds with the ouput of the provided f: I => O") {
          val prefix = "Hello "
          val zArrow = ZArrow.succeed((str: String) => prefix + str)
          check(Gen.string) { str =>
            assertZIO(zArrow(str))(equalTo(prefix + str))
          }
        },
        test("succeeds with the provided by name parameter") {
          val expected = "Something"
          val zArrow   = ZArrow.succeed(expected)
          check(genAny) { any =>
            assertZIO(zArrow(any))(equalTo(expected))
          }
        },
        test("dies when the provided f: I => R => O throws") {
          val exception = new Exception("unexpected!")
          val zArrow = ZArrow.succeed { (int: Int) => (str: String) =>
            throw exception
            1
          }
          check(Gen.int) { int =>
            assertZIO(zArrow(int).provide(ZLayer.succeed("Test")).exit)(dies(equalTo(exception)))
          }
        },
        test("dies when the provided f: I => O throws") {
          val exception = new Exception("unexpected!")
          val zArrow = ZArrow.succeed { (int: Int) =>
            throw exception
            1
          }
          check(Gen.int) { int =>
            assertZIO(zArrow(int).exit)(dies(equalTo(exception)))
          }
        },
        test("dies when the provided by name parameter throws") {
          val exception = new Exception("unexpected!")
          val zArrow = ZArrow.succeed {
            throw exception
            1
          }
          check(genAny) { any =>
            assertZIO(zArrow(any).exit)(dies(equalTo(exception)))
          }
        }
      ),
      suite(".fromZIO")(
        test("succeeds with the ouput of the provided f: I => R1 => ZIO[R, E, O]") {
          val zArrow = ZArrow.fromZIO((str: String) => (prefix: String) => ZIO.succeed(prefix + str))
          check(genTuple(Gen.string)) { (str, prefix) =>
            val eff = zArrow(str).provide(ZLayer.succeed(prefix))
            assertZIO(eff)(equalTo(prefix + str))
          }
        },
        test("succeeds with the ouput of the provided f: I => ZIO[R, E, O]") {
          val prefix = "Hello "
          val zArrow = ZArrow.fromZIO((str: String) => ZIO.succeed(prefix + str))
          check(Gen.string) { str =>
            val eff = zArrow(str)
            assertZIO(eff)(equalTo(prefix + str))
          }
        },
        test("succeeds with the provided by name parameter") {
          val expected = "Something"
          val zArrow   = ZArrow.fromZIO(ZIO.succeed(expected))
          check(genAny) { any =>
            val eff = zArrow(any)
            assertZIO(eff)(equalTo(expected))
          }
        },
        test("dies when the provided f: I => R1 => ZIO[R, E, O] throws") {
          val exception = new Exception("unexpected!")
          val zArrow = ZArrow.fromZIO { (int: Int) => (str: String) =>
            throw exception
            ZIO.succeed(1)
          }
          check(Gen.int) { int =>
            assertZIO(zArrow(int).provide(ZLayer.succeed("Test")).exit)(dies(equalTo(exception)))
          }
        },
        test("dies when the provided f: I => ZIO[R, E, O] throws") {
          val exception = new Exception("unexpected!")
          val zArrow = ZArrow.fromZIO { (int: Int) =>
            throw exception
            ZIO.succeed(1)
          }
          check(Gen.int) { int =>
            val a = zArrow(int).exit
            assertZIO(a)(dies(equalTo(exception)))
          }
        },
        test("dies when the provided by name parameter throws") {
          val exception = new Exception("unexpected!")
          val zArrow = ZArrow.fromZIO {
            throw exception
            ZIO.succeed(1)
          }
          check(genAny) { any =>
            assertZIO(zArrow(any).exit)(dies(equalTo(exception)))
          }
        }
      ),
      suite(".attempt")(
        test("succeeds with the ouput of the provided f: I => R => O") {
          val zArrow = ZArrow.succeed((num: Int) => (div: Int) => num / div)
          check(genTuple(Gen.int, Gen.int.filter(_ != 0))) { (num, div) =>
            val eff = zArrow(num).provide(ZLayer.succeed(div))
            assertZIO(eff)(equalTo(num / div))
          }
        },
        test("succeeds with the ouput of the provided f: I => O") {
          val div    = 2
          val zArrow = ZArrow.succeed((num: Int) => num / div)
          check(Gen.int) { num =>
            val eff = zArrow(num)
            assertZIO(eff)(equalTo(num / div))
          }
        },
        test("succeeds with the provided by name parameter") {
          val expected = 1
          val zArrow   = ZArrow.succeed(expected)
          check(genAny) { any =>
            val eff = zArrow(any)
            assertZIO(eff)(equalTo(expected))
          }
        },
        test("fails when the provided f: I => R => O throws") {
          val exception = new Exception("unexpected!")
          val zArrow = ZArrow.attempt { (int: Int) => (str: String) =>
            throw exception
            1
          }
          check(Gen.int) { int =>
            assertZIO(zArrow(int).provide(ZLayer.succeed("Test")).exit)(fails(equalTo(exception)))
          }
        },
        test("fails when the provided f: I => O throws") {
          val exception = new Exception("unexpected!")
          val zArrow = ZArrow.attempt { (int: Int) =>
            throw exception
            1
          }
          check(Gen.int) { int =>
            assertZIO(zArrow(int).exit)(fails(equalTo(exception)))
          }
        },
        test("fails when the provided by name parameter throws") {
          val exception = new Exception("unexpected!")
          val zArrow = ZArrow.attempt {
            throw exception
            1
          }
          check(genAny) { any =>
            assertZIO(zArrow(any).exit)(fails(equalTo(exception)))
          }
        }
      ),
      suite(".fromZIOAttempt")(
        test("succeeds with the ouput of the provided f: I => R1 => ZIO[R, E, O]") {
          val zArrow = ZArrow.fromZIOAttempt((str: String) => (prefix: String) => ZIO.succeed(prefix + str))
          check(genTuple(Gen.string)) { (str, prefix) =>
            val eff = zArrow(str).provide(ZLayer.succeed(prefix))
            assertZIO(eff)(equalTo(prefix + str))
          }
        },
        test("succeeds with the ouput of the provided f: I => ZIO[R, E, O]") {
          val prefix = "Hello "
          val zArrow = ZArrow.fromZIOAttempt((str: String) => ZIO.succeed(prefix + str))
          check(Gen.string) { str =>
            val eff = zArrow(str)
            assertZIO(eff)(equalTo(prefix + str))
          }
        },
        test("succeeds with the provided by name parameter") {
          val expected = "Something"
          val zArrow   = ZArrow.fromZIOAttempt(ZIO.succeed(expected))
          check(genAny) { any =>
            val eff = zArrow(any)
            assertZIO(eff)(equalTo(expected))
          }
        },
        test("fails when the provided f: I => R1 => ZIO[R, E, O] throws") {
          val exception = new Exception("unexpected!")
          val zArrow = ZArrow.fromZIOAttempt { (int: Int) => (str: String) =>
            throw exception
            ZIO.succeed(1)
          }
          check(Gen.int) { int =>
            assertZIO(zArrow(int).provide(ZLayer.succeed("Test")).exit)(fails(equalTo(exception)))
          }
        },
        test("fails when the provided f: I => ZIO[R, E, O] throws") {
          val exception = new Exception("unexpected!")
          val zArrow = ZArrow.fromZIOAttempt { (int: Int) =>
            throw exception
            ZIO.succeed(1)
          }
          check(Gen.int) { int =>
            assertZIO(zArrow(int).exit)(fails(equalTo(exception)))
          }
        },
        test("fails when the provided by name parameter throws") {
          val exception = new Exception("unexpected!")
          val zArrow = ZArrow.fromZIOAttempt {
            throw exception
            ZIO.succeed(1)
          }
          check(genAny) { any =>
            assertZIO(zArrow(any).exit)(fails(equalTo(exception)))
          }
        }
      ),
      suite(".layer")(
        test("builds a ZLayer wrapping the ZArrow") {
          val zArrow                                        = ZArrow.identity[Int].map(_ * 2)
          val layer: ULayer[ZArrow[Int, Any, Nothing, Int]] = zArrow.layer
          check(Gen.int) { int =>
            val io = for {
              zArrow <- ZArrow.service[Int, Any, Nothing, Int]
              result <- zArrow(int)
            } yield result
            assertZIO(io.provide(layer))(equalTo(int * 2))
          }
        }
      ),
      suite(".apply")(
        test("maps individual inputs to the expected output") {
          val zArrow = ZArrow.succeed((i: Int) => i * 2)
          check(Gen.int) { int =>
            assertZIO(zArrow(int))(equalTo(int * 2))
          }
        }
      ),
      suite(".combine")(
        test("applies the correct ZArrow to each component of the tuple") {
          val adder         = ZArrow.succeed((int: Int) => int + 1)
          val multiplier    = ZArrow.succeed((int: Int) => int * 2)
          val combineZArrow = adder.combine(multiplier)
          check(genTuple(Gen.int)) { (a, b) =>
            assertZIO(combineZArrow((a, b)))(equalTo(a + 1, b * 2))
          }
        },
        test("applies the ZArrow that was combined before the one that was passed") {
          val fAdd          = (int: Int, enqueue: Enqueue[Int]) => enqueue.offer(int + 1)
          val fMultiply     = (int: Int, enqueue: Enqueue[Int]) => enqueue.offer(int * 2)
          val adder         = ZArrow.fromZIO(fAdd.tupled)
          val multiplier    = ZArrow.fromZIO(fMultiply.tupled)
          val combineZArrow = adder.combine(multiplier)
          check(genTuple(Gen.int)) { (a, b) =>
            for
              queue <- Queue.unbounded[Int]
              _     <- combineZArrow(((a, queue), (b, queue)))
              items <- queue.takeAll
            yield assert(items)(equalTo(Chunk(a + 1, b * 2)))
          }
        }
      ),
      suite(".combinePar")(
        test("applies the correct ZArrow to each component of the tuple") {
          val adder            = ZArrow.succeed((int: Int) => int + 1)
          val multiplier       = ZArrow.succeed((int: Int) => int * 2)
          val combineParZArrow = adder.combinePar(multiplier)
          check(genTuple(Gen.int)) { (a, b) =>
            assertZIO(combineParZArrow((a, b)))(equalTo((a + 1), (b * 2)))
          }
        }
      ),
      suite(".zip")(
        test("applies the ZArrow that was zipped before the one that was passed") {
          val fAdd       = (int: Int, enqueue: Enqueue[Int]) => enqueue.offer(int + 1)
          val fMultiply  = (int: Int, enqueue: Enqueue[Int]) => enqueue.offer(int * 2)
          val adder      = ZArrow.fromZIO(fAdd.tupled)
          val multiplier = ZArrow.fromZIO(fMultiply.tupled)
          val zipZArrow  = adder.zip(multiplier)
          check(Gen.int) { int =>
            for
              queue <- Queue.unbounded[Int]
              _     <- zipZArrow((int, queue))
              items <- queue.takeAll
            yield assert(items)(equalTo(Chunk(int + 1, int * 2)))
          }
        }
      ),
      suite(".zipPar")(
        test("applies both ZArrow to the input") {
          val adder        = ZArrow.succeed((int: Int) => int + 1)
          val multiplier   = ZArrow.succeed((int: Int) => int * 2)
          val zipParZArrow = adder.zipPar(multiplier)
          check(Gen.int) { int =>
            assertZIO(zipParZArrow(int))(equalTo((int + 1, int * 2)))
          }
        }
      ),
      suite(".compose")(
        test("applies the original ZArrow to the zio successes returned by the one that was passed") {
          val adder         = ZArrow.succeed((int: Int) => int + 1)
          val multiplier    = ZArrow.succeed((int: Int) => int * 2)
          val composeZArrow = adder.compose(multiplier)
          check(Gen.int) { int =>
            assertZIO(composeZArrow(int))(equalTo((int * 2) + 1))
          }
        }
      ),
      suite(".andThen")(
        test("applies the ZArrow that was passed to the zio successes returned by the original one") {
          val adder         = ZArrow.succeed((int: Int) => int + 1)
          val multiplier    = ZArrow.succeed((int: Int) => int * 2)
          val andThenZArrow = adder.andThen(multiplier)
          check(Gen.int) { int =>
            assertZIO(andThenZArrow(int))(equalTo((int + 1) * 2))
          }
        }
      ),
      suite(".errorCompose")(
        test("applies the original ZArrow to the zio failures returned by the one that was passed") {
          val thrower            = ZArrow.fromZIO((int: Int) => ZIO.fail(int))
          val errorHandler       = ZArrow.succeed((int: Int) => int * 2)
          val errorComposeZArrow = errorHandler.errorCompose(thrower)
          check(Gen.int) { int =>
            assertZIO(errorComposeZArrow(int).exit)(fails(equalTo(int * 2)))
          }
        }
      ),
      suite(".errorAndThen")(
        test("applies the ZArrow that was passed to the zio failures returned by the original one") {
          val thrower            = ZArrow.fromZIO((int: Int) => ZIO.fail(int))
          val errorHandler       = ZArrow.succeed((int: Int) => int * 2)
          val errorAndThenZArrow = thrower.errorAndThen(errorHandler)
          check(Gen.int) { int =>
            assertZIO(errorAndThenZArrow(int).exit)(fails(equalTo(int * 2)))
          }
        }
      ),
      suite("catchAll")(
        test(
          "applies the provided ZArrow to the failures returned by the original ZArrow and recovers in case it returns a succeeding ZIO"
        ) {
          val failingZArrow = ZArrow.fromZIO((i: Int) => ZIO.fail(i))
          val zArrow        = failingZArrow.catchAll(ZArrow.identity)
          check(Gen.int) { int =>
            assertZIO(zArrow(int))(equalTo(int))
          }
        },
        test(
          "applies the provided ZArrow to the failures returned by the original ZArrow and doesn't recover in case it returns a failing ZIO"
        ) {
          val failingZArrow = ZArrow.fromZIO((i: Int) => ZIO.fail(i))
          val zArrow        = failingZArrow.catchAll(failingZArrow)
          check(Gen.int) { int =>
            assertZIO(zArrow(int).exit)(fails(equalTo(int)))
          }
        }
      ),
      suite("catchAllCause")(
        test(
          "applies the provided ZArrow to the failures returned by the original ZArrow and recovers in case it returns a succeeding ZIO"
        ) {
          val failingZArrow = ZArrow.fromZIO((i: Int) => ZIO.fail(i))
          val expected      = "recovered"
          val zArrow        = failingZArrow.catchAllCause(ZArrow.succeed(expected))
          check(Gen.int) { int =>
            assertZIO(zArrow(int))(equalTo(expected))
          }
        },
        test(
          "applies the provided ZArrow to the failures returned by the original ZArrow and doesn't recover in case it returns a failing ZIO"
        ) {
          val failingZArrow = ZArrow.fromZIO((i: Int) => ZIO.fail(i))
          val catcherZArrow = ZArrow.fromZIO((cause: Cause[Int]) => ZIO.failCause(cause))
          val zArrow        = failingZArrow.catchAllCause(catcherZArrow)
          check(Gen.int) { int =>
            assertZIO(zArrow(int).exit)(fails(equalTo(int)))
          }
        },
        test(
          "applies the provided ZArrow to the defects returned by the original ZArrow and recovers in case it returns a succeeding ZIO"
        ) {
          val exception   = new Exception("unexpected!")
          val expected    = "recovered"
          val dyingZArrow = ZArrow.fromZIO(ZIO.die(exception))
          val zArrow      = dyingZArrow.catchAllCause(ZArrow.succeed(expected))
          check(Gen.int) { int =>
            assertZIO(zArrow(int))(equalTo(expected))
          }
        },
        test(
          "applies the provided ZArrow to the defects returned by the original ZArrow and doesn't recover in case it returns a failing ZIO"
        ) {
          val exception     = new Exception("unexpected!")
          val dyingZArrow   = ZArrow.fromZIO(ZIO.die(exception))
          val catcherZArrow = ZArrow.fromZIO((cause: Cause[Int]) => ZIO.fail(1))
          val zArrow        = dyingZArrow.catchAllCause(catcherZArrow)
          check(Gen.int) { int =>
            assertZIO(zArrow(int).exit)(fails(equalTo(1)))
          }
        }
      ),
      suite(".mapZIO")(
        test("applies the provided function to the zio successes returned by the original ZArrow") {
          val zArrow = ZArrow.identity[Int].mapZIO(_.map(_ * 2))
          check(Gen.int) { int =>
            assertZIO(zArrow(int))(equalTo(int * 2))
          }
        },
        test("applies the provided function to the zio failures returned by the original ZArrow") {
          val failingZArrow = ZArrow.fromZIO((int: Int) => ZIO.fail(int))
          val f             = (zio: ZIO[Any, Int, Nothing]) => zio.catchAll(int => ZIO.succeed(int * 2))
          val zArrow        = failingZArrow.mapZIO(f)
          check(Gen.int) { int =>
            assertZIO(zArrow(int))(equalTo(int * 2))
          }
        },
        test("dies when the provided function throws") {
          val expected = new Exception("unexpected")
          val f        = (zio: ZIO[Any, Nothing, Int]) => throw expected
          val zArrow   = ZArrow.identity[Int].mapZIO(f)
          check(Gen.int) { int =>
            assertZIO(zArrow(int).exit)(dies(equalTo(expected)))
          }
        }
      ),
      suite(".map")(
        test("applies the provided function to the zio successes returned by the original ZArrow") {
          val zArrow = ZArrow.identity[Int].map(_ * 2)
          check(Gen.int) { int =>
            assertZIO(zArrow(int))(equalTo(int * 2))
          }
        },
        test("dies when the provided function throws") {
          val expected = new Exception("unexpected")
          val f        = (int: Int) => throw expected
          val zArrow   = ZArrow.identity[Int].map(f)
          check(Gen.int) { int =>
            assertZIO(zArrow(int).exit)(dies(equalTo(expected)))
          }
        }
      ),
      suite(".mapAttempt")(
        test("applies the provided function to the zio succcesses returned by the original ZArrow") {
          val zArrow = ZArrow.identity[Int].mapAttempt(_ * 2)
          check(Gen.int) { int =>
            assertZIO(zArrow(int))(equalTo(int * 2))
          }
        },
        test("fails when the provided function throws") {
          val expected = new Exception("unexpected")
          val f        = (int: Int) => throw expected
          val zArrow   = ZArrow.identity[Int].mapAttempt(f)
          check(Gen.int) { int =>
            assertZIO(zArrow(int).exit)(fails(equalTo(expected)))
          }
        }
      ),
      suite(".mapError")(
        test("applies the provided function to the zio failures returned by the original ZArrow") {
          val zArrow = ZArrow.fromZIO((int: Int) => ZIO.fail(int)).mapError(_ * 2)
          check(Gen.int) { int =>
            assertZIO(zArrow(int).exit)(fails(equalTo(int * 2)))
          }
        },
        test("dies when the provided function throws") {
          val expected = new Exception("unexpected")
          val f        = (int: Int) => throw expected
          val zArrow   = ZArrow.fromZIO((int: Int) => ZIO.fail(int)).mapError(f)
          check(Gen.int) { int =>
            assertZIO(zArrow(int).exit)(dies(equalTo(expected)))
          }
        }
      ),
      suite("mapErrorAttempt")(
        test("applies the provided function to the zio failures returned by the original ZArrow") {
          val zArrow = ZArrow.fromZIO((int: Int) => ZIO.fail(int)).mapErrorAttempt(_ * 2)
          check(Gen.int) { int =>
            assertZIO(zArrow(int).exit)(fails(equalTo(int * 2)))
          }
        },
        test("fails when the provided function throws") {
          val expected = new Exception("unexpected")
          val f        = (int: Int) => throw expected
          val zArrow   = ZArrow.fromZIO((int: Int) => ZIO.fail(int)).mapErrorAttempt(f)
          check(Gen.int) { int =>
            assertZIO(zArrow(int).exit)(fails(equalTo(expected)))
          }
        }
      ),
      suite(".mapBoth")(
        test("applies the provided function fO to the zio succcesses returned by the original ZArrow") {
          val zArrow = ZArrow.identity[Int].mapBoth(scala.Predef.identity, _ * 2)
          check(Gen.int) { int =>
            assertZIO(zArrow(int))(equalTo(int * 2))
          }
        },
        test("dies when the provided function fO throws") {
          val expected = new Exception("unexpected")
          val f        = (int: Int) => throw expected
          val zArrow   = ZArrow.identity[Int].mapBoth(scala.Predef.identity, f)
          check(Gen.int) { int =>
            assertZIO(zArrow(int).exit)(dies(equalTo(expected)))
          }
        },
        test("applies the provided function fE to the zio failures returned by the original ZArrow") {
          val zArrow = ZArrow.fromZIO((int: Int) => ZIO.fail(int)).mapBoth(_ * 2, scala.Predef.identity)
          check(Gen.int) { int =>
            assertZIO(zArrow(int).exit)(fails(equalTo(int * 2)))
          }
        },
        test("dies when the provided function fE throws") {
          val expected = new Exception("unexpected")
          val f        = (int: Int) => throw expected
          val zArrow   = ZArrow.fromZIO((int: Int) => ZIO.fail(int)).mapBoth(f, scala.Predef.identity)
          check(Gen.int) { int =>
            assertZIO(zArrow(int).exit)(dies(equalTo(expected)))
          }
        }
      ),
      suite(".imap")(
        test("applies the provided function to the inputs and forwards its output to the original ZArrow") {
          val zArrow = ZArrow.succeed((int: Int) => int * 2).imapAttempt((str: String) => str.toInt)
          check(Gen.int) { int =>
            assertZIO(zArrow(int.toString))(equalTo(int * 2))
          }
        },
        test("dies when the provided function throws") {
          val expected = new Exception("unexpected")
          val f        = (int: Int) => throw expected
          val zArrow   = ZArrow.identity[Int].imap(f)
          check(Gen.int) { int =>
            assertZIO(zArrow(int).exit)(dies(equalTo(expected)))
          }
        }
      ),
      suite(".imapAttempt")(
        test("applies the provided function to the inputs and forwards its output to the original ZArrow") {
          val zArrow = ZArrow.succeed((int: Int) => int * 2).imapAttempt((str: String) => str.toInt)
          check(Gen.int) { int =>
            assertZIO(zArrow(int.toString))(equalTo(int * 2))
          }
        },
        test("fails when the provided function throws") {
          val expected = new Exception("unexpected")
          val f        = (int: Int) => throw expected
          val zArrow   = ZArrow.identity[Int].imapAttempt(f)
          check(Gen.int) { int =>
            assertZIO(zArrow(int).exit)(fails(equalTo(expected)))
          }
        }
      ),
      suite(".flatMap")(
        test("applies the provided function and the ZArrow it gives to the successes of the origianl ZArrow") {
          val adder = ZArrow.succeed((int: Int) => int * 2)
          val zArrow = adder.flatMap { (a: Int) =>
            ZArrow.succeed((b: Int) => (b * a) + 1)
          }
          check(genTuple(Gen.int)) { (a, b) =>
            assertZIO(zArrow((a, b)))(equalTo(((a * 2) * b) + 1))
          }
        },
        test("dies when the provided function throws") {
          val expected = new Exception("unexpected")
          val f        = (int: Int) => throw expected
          val zArrow   = ZArrow.identity[Int].flatMap(f)
          check(Gen.int) { int =>
            assertZIO(zArrow((int, int)).exit)(dies(equalTo(expected)))
          }
        }
      ),
      suite(".flatMapError")(
        test("applies the provided function and the ZArrow it gives to the failures of the original ZArrow") {
          val failingAdder = ZArrow.fromZIO((int: Int) => ZIO.fail(int * 2))
          val zArrow = failingAdder.flatMapError { (a: Int) =>
            ZArrow.succeed((b: Int) => (b * a) + 1)
          }
          check(genTuple(Gen.int)) { (a, b) =>
            assertZIO(zArrow((a, b)).exit)(fails(equalTo(((a * 2) * b) + 1)))
          }
        },
        test("dies when the provided function throws") {
          val failingAdder = ZArrow.fromZIO((int: Int) => ZIO.fail(int * 2))
          val expected     = new Exception("unexpected")
          val f            = (int: Int) => throw expected
          val zArrow       = failingAdder.flatMapError(f)
          check(Gen.int) { int =>
            assertZIO(zArrow((int, int)).exit)(dies(equalTo(expected)))
          }
        }
      ),
      suite(".flatMapBoth")(
        test("applies fO and the ZArrow it gives to the successes of the original ZArrow") {
          val adder         = ZArrow.succeed((int: Int) => int * 2)
          val followUp      = (a: Int) => ZArrow.identity[Int].map(b => (b * a) + 1)
          val errorFollowUp = (a: Int) => ZArrow.identity[Int].map(b => (b * a) + 2)
          val zArrow        = adder.flatMapBoth(errorFollowUp, followUp)
          check(genTuple(Gen.int)) { (a, b) =>
            assertZIO(zArrow(((a, b), b)))(equalTo(((a * 2) * b) + 1))
          }
        },
        test("applies fE and the ZArrow it gives to the failures of the original ZArrow") {
          val failingAdder  = ZArrow.fromZIO((int: Int) => ZIO.fail(int * 2))
          val followUp      = (a: Int) => ZArrow.identity[Int].map(b => (b * a) + 1)
          val errorFollowUp = (a: Int) => ZArrow.identity[Int].map(b => (b * a) + 2)
          val zArrow        = failingAdder.flatMapBoth(errorFollowUp, followUp)
          check(genTuple(Gen.int)) { (a, b) =>
            assertZIO(zArrow(((a, b), b)).exit)(fails(equalTo(((a * 2) * b) + 2)))
          }
        },
        test("dies when fO throws") {
          val adder         = ZArrow.succeed((int: Int) => int * 2)
          val expected      = new Exception("unexpected")
          val followUp      = (a: Int) => throw expected
          val errorFollowUp = (a: Int) => ZArrow.identity[Int].map(b => (b * a) + 2)
          val zArrow        = adder.flatMapBoth(errorFollowUp, followUp)
          check(genTuple(Gen.int)) { (a, b) =>
            assertZIO(zArrow(((a, b), b)).exit)(dies(equalTo(expected)))
          }
        },
        test("dies when fE throws") {
          val failingAdder  = ZArrow.fromZIO((int: Int) => ZIO.fail(int * 2))
          val followUp      = (a: Int) => ZArrow.identity[Int].map(b => (b * a) + 1)
          val expected      = new Exception("unexpected")
          val errorFollowUp = (a: Int) => throw expected
          val zArrow        = failingAdder.flatMapBoth(errorFollowUp, followUp)
          check(genTuple(Gen.int)) { (a, b) =>
            assertZIO(zArrow(((a, b), b)).exit)(dies(equalTo(expected)))
          }
        }
      ),
      suite("withFilter")(
        test(
          "returns a ZArrow that succeeds with Some(s) when the predicate gives true for the successes s of the original ZArrow"
        ) {
          val zArrow = ZArrow.identity[Int].withFilter(_ => true)
          check(Gen.int) { int =>
            assertZIO(zArrow(int))(isSome(equalTo(int)))
          }
        },
        test(
          "returns a ZArrow that succeeds with None when the predicate gives false for the successes of the original ZArrow"
        ) {
          val zArrow = ZArrow.identity[Int].withFilter(_ => false)
          check(Gen.int) { int =>
            assertZIO(zArrow(int))(isNone)
          }
        }
      ),
      suite(".swapInputs")(
        test("reverts the order of the input tuples") {
          val idInt    = ZArrow.identity[Int]
          val toString = ZArrow.identity[Any].map(_.toString())
          val zArrow   = idInt.combine(toString).swapInputs
          check(genTuple(Gen.int)) { (a, b) =>
            assertZIO(zArrow((a, b)))(equalTo((b, a.toString())))
          }
        }
      ),
      suite(".swapOutputs")(
        test("reverts the order of the output tuples") {
          val idInt    = ZArrow.identity[Int]
          val toString = ZArrow.identity[Any].map(_.toString())
          val zArrow   = idInt.combine(toString).swapOutputs
          check(genTuple(Gen.int)) { (a, b) =>
            assertZIO(zArrow((a, b)))(equalTo((b.toString(), a)))
          }
        }
      )
    )

// See LICENSE for license details.
package chisel3.iotesters

import java.io.PrintStream

import chisel3._
import chisel3.internal.InstanceId
import firrtl_interpreter.InterpretiveTester

private[iotesters] class FirrtlTerpBackend(
    dut: Module,
    firrtlIR: String,
    optionsManager: TesterOptionsManager = new TesterOptionsManager)
  extends Backend(_seed = System.currentTimeMillis()) {
  val interpretiveTester = new InterpretiveTester(firrtlIR, optionsManager)
  reset(5) // reset firrtl interpreter on construction

  private val portNames = getDataNames("io", dut.io).toMap

  def poke(signal: InstanceId, value: BigInt, off: Option[Int])
          (implicit logger: TestErrorLog, verbose: Boolean, base: Int): Unit = {
    signal match {
      case port: Bits =>
        val name = portNames(port)
        interpretiveTester.poke(name, value)
        if (verbose) logger info s"  POKE $name <- ${bigIntToStr(value, base)}"
      case _ =>
    }
  }

  def poke(signal: InstanceId, value: Int, off: Option[Int])
          (implicit logger: TestErrorLog, verbose: Boolean, base: Int): Unit = {
    poke(signal, BigInt(value), off)
  }

  def peek(signal: InstanceId, off: Option[Int])
          (implicit logger: TestErrorLog, verbose: Boolean, base: Int): BigInt = {
    signal match {
      case port: Bits =>
        val name = portNames(port)
        val result = interpretiveTester.peek(name)
        if (verbose) logger info s"  PEEK $name -> ${bigIntToStr(result, base)}"
        result
      case _ => BigInt(rnd.nextInt)
    }
  }

  def expect(signal: InstanceId, expected: BigInt, msg: => String)
            (implicit logger: TestErrorLog, verbose: Boolean, base: Int) : Boolean = {
    signal match {
      case port: Bits =>
        val name = portNames(port)
        val got = interpretiveTester.peek(name)
        val good = got == expected
        if (verbose || !good) logger info
           s"""EXPECT AT $stepNumber $msg  $name got ${bigIntToStr(got, base)} expected ${bigIntToStr(expected, base)}""" +
           s""" ${if (good) "PASS" else "FAIL"}"""
        if(good) interpretiveTester.expectationsMet += 1
        good
      case _ => false
    }
  }

  def expect(signal: InstanceId, expected: Int, msg: => String)
            (implicit logger: TestErrorLog, verbose: Boolean, base: Int) : Boolean = {
    expect(signal,BigInt(expected), msg)
  }

  def poke(path: String, value: BigInt)
          (implicit logger: TestErrorLog, verbose: Boolean, base: Int): Unit = {
    assert(false)
  }

  def peek(path: String)
          (implicit logger: TestErrorLog, verbose: Boolean, base: Int): BigInt = {
    assert(false)
    BigInt(rnd.nextInt)
  }

  def expect(path: String, expected: BigInt, msg: => String)
            (implicit logger: TestErrorLog, verbose: Boolean, base: Int) : Boolean = {
    assert(false)
    false
  }

  private var stepNumber: Long = 0L

  def step(n: Int)(implicit logger: TestErrorLog): Unit = {
    stepNumber += n
    interpretiveTester.step(n)
  }

  def reset(n: Int = 1): Unit = {
    interpretiveTester.poke("reset", 1)
    interpretiveTester.step(n)
    interpretiveTester.poke("reset", 0)
  }

  def finish(implicit logger: TestErrorLog): Unit = {
    interpretiveTester.report()
  }
}

private[iotesters] object setupFirrtlTerpBackend {
  def apply[T <: chisel3.Module](
      dutGen: () => T,
      optionsManager: TesterOptionsManager = new TesterOptionsManager): (T, Backend) = {

    chisel3.Driver.execute(optionsManager, dutGen) match {
      case ChiselExecutionSuccess(Some(circuit), firrtlText, Some(_)) =>
        val dut = getTopModule(circuit).asInstanceOf[T]
        (dut, new FirrtlTerpBackend(dut, firrtlText, optionsManager = optionsManager))
      case _ =>
        throw new Exception("Problem with compilation")
    }
//
//
//    val circuit = chisel3.Driver.elaborate(dutGen)
//    val dut = getTopModule(circuit).asInstanceOf[T]
//    val dir = new File(s"test_run_dir/${dut.getClass.getName}") ; dir.mkdirs()
//
//    // Dump FIRRTL for debugging
//    chisel3.Driver.dumpFirrtl(circuit, Some(new File(dir, s"${circuit.name}.fir")))
//    (dut, new FirrtlTerpBackend(dut, chisel3.Driver.emit(dutGen)))
  }
}

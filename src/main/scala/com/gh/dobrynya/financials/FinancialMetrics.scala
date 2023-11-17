package com.gh.dobrynya.financials

import zio.*
import zio.stream.*
import java.nio.file.Paths
import java.time.*
import java.time.Month.NOVEMBER
import java.time.format.DateTimeFormatter
import java.util.Locale
import scala.collection.immutable.SortedSet
import scala.util.Try

case class Instrument(name: String, date: LocalDate, price: Double)

object Instrument:
  def parse(s: String): Option[Instrument] = Try {
    val p = s.split(",")
    Instrument(p(0), LocalDate.parse(p(1), DateTimeFormatter.ofPattern("dd-MMM-uuuu", Locale.US)), p(2).toDouble)
  }.toOption

extension (f: Instrument => Boolean)
  def and(another: Instrument => Boolean): Instrument => Boolean = (i: Instrument) => f(i) && another(i)

def name(name: String): Instrument => Boolean = (i: Instrument) => i.name == name

def date(from: LocalDate = LocalDate.MIN, to: LocalDate = LocalDate.MAX): Instrument => Boolean =
  (i: Instrument) => i.date.compareTo(from) >= 0 && i.date.compareTo(to) <= 0

def workingDays: Instrument => Boolean =
  (i: Instrument) => i.date.getDayOfWeek != DayOfWeek.SUNDAY && i.date.getDayOfWeek != DayOfWeek.SATURDAY

trait Indicator[+T] extends (Instrument => UIO[Unit]):
  def value: T = throw new IllegalStateException("You cannot call this on base class!")
  def filter(predicate: Instrument => Boolean): Indicator[T] =
    (inst: Instrument) => apply(inst).when(predicate(inst)).unit
  def +(that: Indicator[Any]): Indicator[Any] = (v1: Instrument) => apply(v1) *> that(v1)

class Avg(private var state: (Double, Int) = (0, 0)) extends Indicator[Double]:
  override def apply(v1: Instrument): UIO[Unit] = ZIO.succeed {
    state = (state._1 + v1.price, state._2 + 1)
  }
  override def value: Double = state._1 / state._2

class SumOfLast(n: Int = 10, private var state: SortedSet[Instrument] =
SortedSet()(Ordering.by(_.date))) extends Indicator[Double]:
  override def apply(v1: Instrument): UIO[Unit] = ZIO.succeed {
    val res = state + v1
    state = if (res.size > n) res.drop(1) else res
  }
  override def value: Double = state.map(_.price).sum

object FinancialMetrics extends ZIOAppDefault:
  private val instruments = ZStream.fromPath(Paths.get("instruments.txt"))
    .via(ZPipeline.utfDecode >>> ZPipeline.splitLines).map(Instrument.parse).collectSome
    .filter(date(to = LocalDate.of(2014, NOVEMBER, 19)) and workingDays)

  private val i1mean = new Avg().filter(name("INSTRUMENT1"))
  private val i2mean4november2014 = new Avg().filter(name("INSTRUMENT2") and
    date(LocalDate.of(2014, NOVEMBER, 1), LocalDate.of(2014, NOVEMBER, 30)))
  private val i3sumLast = new SumOfLast().filter(name("INSTRUMENT3"))

  override def run: IO[Throwable, Unit] =
    instruments.foreach(i1mean + i2mean4november2014 + i3sumLast) *>
      Console.printLine("Completed reading file") *>
      Console.printLine(s"Mean for INSTRUMENT1 is ${i1mean.value}") *>
      Console.printLine(s"Mean for INSTRUMENT2 for November 2014 is ${i2mean4november2014.value}") *>
      Console.printLine(s"Sum of 10 recent prices for INSTRUMENT3 is ${i3sumLast.value}")

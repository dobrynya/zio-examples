package com.gh.dobrynya.financials

import zio.*
import clock.*
import console.*
import blocking.Blocking
import zio.stream.*
import java.nio.file.Paths
import java.time.{DayOfWeek, Duration, LocalDate, Month}
import java.time.format.DateTimeFormatter
import java.util.Locale
import scala.collection.immutable.SortedSet
import scala.util.Try
import java.time.Month.NOVEMBER

extension (f: Instrument => Boolean)
  def and(another: Instrument => Boolean) = (i: Instrument) => f(i) && another(i)
  def or(another: Instrument => Boolean) = (i: Instrument) => f(i) && another(i)

case class Instrument(name: String, date: LocalDate, price: Double)

object Instrument:
  def parse(s: String): Option[Instrument] = Try {
    val p = s.split(",")
    Instrument(p(0), LocalDate.parse(p(1), DateTimeFormatter.ofPattern("dd-MMM-uuuu", Locale.US)), p(2).toDouble)
  }.toOption

trait Indicator[T] extends (Instrument => UIO[Unit]) :
  self =>
  def value: T = ???
  def filter(predicate: Instrument => Boolean): Indicator[T] =
    new Indicator[T] :
      override def value: T = self.value
      override def apply(inst: Instrument) = self(inst).when(predicate(inst))
  def +(that: Indicator[?]): Indicator[Any] = (v1: Instrument) => self.apply(v1) *> that(v1)

class Avg(private var state: (Double, Int) = (0, 0)) extends Indicator[Double] :
  override def apply(v1: Instrument) = UIO {
    state = (state._1 + v1.price, state._2 + 1)
  }
  override def value: Double = state._1 / state._2

class SumOfLast(n: Int = 10, private var state: SortedSet[Instrument] = SortedSet()(Ordering.by(_.date))) extends Indicator[Double] :
  override def apply(v1: Instrument) = UIO {
    val res = state + v1
    state = if (res.size > n) res.drop(1) else res
  }
  override def value = state.map(_.price).sum

def name(name: String) = (i: Instrument) => i.name == name

def date(from: LocalDate = LocalDate.MIN, to: LocalDate = LocalDate.MAX) =
  (i: Instrument) => i.date.compareTo(from) >= 0 && i.date.compareTo(to) <= 0

val workingDays: (Instrument => Boolean) =
  (i: Instrument) => i.date.getDayOfWeek != DayOfWeek.SUNDAY && i.date.getDayOfWeek != DayOfWeek.SATURDAY

object FinancialMetrics extends App :
  val instruments: ZStream[Blocking, Throwable, Instrument] =
    (ZStream.fromFile(Paths.get("instruments.txt")) >>> ZTransducer.utfDecode >>> ZTransducer.splitLines)
      .map(Instrument.parse).collectSome
      .filter(date(to = LocalDate.of(2014, NOVEMBER, 19)) and workingDays)
  val i1mean = new Avg().filter(name("INSTRUMENT1"))
  val i2mean4november2014 = new Avg().filter(name("INSTRUMENT2") and
    date(LocalDate.of(2014, NOVEMBER, 1), LocalDate.of(2014, NOVEMBER, 30)))
  val i3sumLast = new SumOfLast().filter(name("INSTRUMENT3"))

  override def run(args: List[String]) = time {
    instruments.repeat(Schedule.recurs(10)).foreach(i1mean + i2mean4november2014 + i3sumLast) *>
      console.putStrLn("Completed reading file") *>
      console.putStrLn(s"Mean for INSTRUMENT1 is ${i1mean.value}") *>
      console.putStrLn(s"Mean for INSTRUMENT2 for November 2014 is ${i2mean4november2014.value}") *>
      console.putStrLn(s"Sum of 10 recent prices for INSTRUMENT3 is ${i3sumLast.value}")
  }.exitCode

def time[R, E, A](effect: ZIO[R, E, A]): ZIO[R & Console & Clock, E, A] =
  for {
    start <- currentDateTime.orDie
    result <- effect
    end <- currentDateTime.orDie
    _ <- putStrLn(s"Evaluating effect took ${Duration.between(start, end).toMillis} ms").orDie
  } yield result
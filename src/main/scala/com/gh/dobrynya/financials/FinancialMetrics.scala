package com.gh.dobrynya.financials

import zio.*
import zio.stream.*
import java.io.File
import java.nio.file.Paths
import java.time.{DayOfWeek, LocalDate, Month}
import java.time.format.DateTimeFormatter
import java.util.Locale
import scala.collection.immutable.SortedSet
import scala.util.Try
import java.time.LocalDate
import java.time.Month.NOVEMBER

extension (f: Instrument => Boolean)
  def and(another: Instrument => Boolean) = (i: Instrument) => f(i) && another(i)
  def or(another: Instrument => Boolean) = (i: Instrument) => f(i) && another(i)

case class Instrument(name: String, date: LocalDate, price: Double)

object Instrument:
  def parse(s: String) = Try {
    val p = s.split(",")
    Instrument(p(0), LocalDate.parse(p(1), DateTimeFormatter.ofPattern("dd-MMM-uuuu", Locale.US)), p(2).toDouble)
  }.toOption

def name(name: String) = (i: Instrument) => i.name == name

def date(from: LocalDate = LocalDate.MIN, to: LocalDate = LocalDate.MAX) =
  (i: Instrument) => i.date.compareTo(from) >= 0 && i.date.compareTo(to) <= 0

val workingDays: (Instrument => Boolean) =
  (i: Instrument) => i.date.getDayOfWeek != DayOfWeek.SUNDAY && i.date.getDayOfWeek != DayOfWeek.SATURDAY

object FinancialMetrics extends ZIOAppDefault:
  val raw =
    ZStream.fromFile(new File("instruments.txt")).via(ZPipeline.utfDecode).via(ZPipeline.splitLines)

  val instruments: ZStream[Any, Throwable, Instrument] =
    raw.map(Instrument.parse).collectSome
      .filter(date(to = LocalDate.of(2014, NOVEMBER, 19)) and workingDays)

  val mean: Sink[Nothing, Instrument, Nothing, Double] = ZSink.foldLeft((0.0, 0)) { (s, elem: Instrument) =>
    (s._1 + elem.price, s._2 + 1)
  }.map(s => s._1 / s._2)

  def sumLast(n: Int) =
    ZSink.foldLeft(SortedSet[Instrument]()(Ordering.by(_.date))) { (s, i: Instrument) =>
      val result = s + i
      if (result.size > n) result.drop(1) else result
    }.map(_.map(_.price).sum)

  def makeCalculation[R](hub: Hub[Instrument], sink: Sink[Nothing, Instrument, Nothing, R], filter: Instrument => Boolean): URIO[Any, Fiber.Runtime[Nothing, R]] =
    ZStream.fromHub(hub).filter(filter).run(sink).fork

  override def run =
    (for {
      hub <- ZHub.unbounded[Instrument]
      i1mean <- makeCalculation(hub, mean, name("INSTRUMENT1"))
      i2mean4november2014 <- makeCalculation(hub, mean, name("INSTRUMENT2") and
        date(LocalDate.of(2014, NOVEMBER, 1), LocalDate.of(2014, NOVEMBER, 30)))
      i3sumLast <- makeCalculation(hub, sumLast(10), name("INSTRUMENT3"))
      _ <- instruments.foreach(hub.publish)
      _ <- Console.printLine("Completed reading file")
      _ <- hub.shutdown
      _ <- i1mean.join.flatMap(d => Console.printLine(s"Mean for INSTRUMENT1 is $d"))
      _ <- i2mean4november2014.join.flatMap(d => Console.printLine(s"Mean for INSTRUMENT2 for November 2014 is $d"))
      _ <- i3sumLast.join.flatMap(d => Console.printLine(s"Sum of 10 recent prices for INSTRUMENT3 is $d"))
    } yield ()).exitCode

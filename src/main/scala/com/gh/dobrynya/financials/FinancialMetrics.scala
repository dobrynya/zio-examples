package com.gh.dobrynya.financials

import zio.*
import blocking.Blocking
import zio.stream.*
import java.nio.file.Paths
import java.time.{DayOfWeek, LocalDate, Month}
import java.time.format.DateTimeFormatter
import java.util.Locale
import scala.collection.immutable.SortedSet
import scala.util.Try
import java.time.LocalDate

case class Instrument(name: String, date: LocalDate, price: Double)

object Instrument:
  def parse(s: String) = Try {
    val p = s.split(",")
    Instrument(p(0), LocalDate.parse(p(1), DateTimeFormatter.ofPattern("dd-MMM-uuuu", Locale.US)), p(2).toDouble)
  }.toOption

object FinancialMetrics extends App:
  val raw =
    ZStream.fromFile(Paths.get("instruments.txt")) >>> ZTransducer.utfDecode >>> ZTransducer.splitLines

  val instruments =
    raw.map(Instrument.parse).collectSome
      .filter(Date(to = LocalDate.of(2014, Month.NOVEMBER, 19)) and WorkingDays)

  val mean: Sink[Nothing, Instrument, Nothing, Double] = ZSink.foldLeft((0.0, 0)) { (s, elem: Instrument) =>
    (s._1 + elem.price, s._2 + 1)
  }.map(s => s._1 / s._2)

  def sumLast(n: Int) =
    ZSink.foldLeft(SortedSet[Instrument]()(Ordering.by(_.date))) { (s, i: Instrument) =>
      val result = s + i
      if (result.size > n) result.drop(1) else result
    }.map(_.map(_.price).sum)

  def makeCalculation[R](hub: Hub[Instrument], sink: Sink[Nothing, Instrument, Nothing, R], filter: Filter): URIO[Any, Fiber.Runtime[Nothing, R]] =
    ZStream.fromHub(hub).filter(filter).run(sink).fork

  override def run(args: List[String]) =
    (for {
      hub <- ZHub.unbounded[Instrument]
      i1mean <- makeCalculation(hub, mean, Name("INSTRUMENT1"))
      i2mean4november2014 <- makeCalculation(hub, mean, Name("INSTRUMENT2") and
            Date(LocalDate.of(2014, Month.NOVEMBER, 1),
              LocalDate.of(2014, Month.NOVEMBER, 30)))
      i3sumLast <- makeCalculation(hub, sumLast(10), Name("INSTRUMENT3"))
      _ <- instruments.foreach(hub.publish)
      _ <- console.putStrLn("Completed reading file")
      _ <- hub.shutdown
      _ <- i1mean.join.flatMap(d => console.putStrLn(s"Mean for INSTRUMENT1 is $d"))
      _ <- i2mean4november2014.join.flatMap(d => console.putStrLn(s"Mean for INSTRUMENT2 for November 2014 is $d"))
      _ <- i3sumLast.join.flatMap(d => console.putStrLn(s"Sum of 10 recent prices for INSTRUMENT3 is $d"))
    } yield ()).exitCode

trait Filter extends (Instrument => Boolean):
  def and(another: Filter): Filter = Predicate(i => apply(i) && another(i))
  def or(another: Filter): Filter = Predicate(i => apply(i) || another(i))

case class Predicate(f: Instrument => Boolean) extends Filter:
  override def apply(i: Instrument): Boolean = f(i)

case class Name(name: String) extends Filter:
  override def apply(i: Instrument): Boolean =
    i.name == name

case class Date(from: LocalDate = LocalDate.MIN, to: LocalDate = LocalDate.MAX) extends Filter:
  override def apply(i: Instrument): Boolean = i.date.compareTo(from) >= 0 && i.date.compareTo(to) <= 0

case object WorkingDays extends Filter:
  val weekend = Set(DayOfWeek.SUNDAY, DayOfWeek.SATURDAY)
  override def apply(i: Instrument): Boolean = !weekend.contains(i.date.getDayOfWeek)

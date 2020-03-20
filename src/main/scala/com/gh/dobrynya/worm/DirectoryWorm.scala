package com.gh.dobrynya.worm

import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{Files, Path, Paths, StandardOpenOption}
import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneId}
import zio._
import zio.clock.Clock
import zio.console.Console
import zio.duration._
import zio.stream._
import scala.jdk.CollectionConverters._

object DirectoryWorm extends App {
  private val format = DateTimeFormatter.ISO_DATE_TIME

  private[worm] def makeFileDescription(file: Path) = {
    val attrs = Files.readAttributes(file, classOf[BasicFileAttributes])
    val date = format.format(LocalDateTime.ofInstant(attrs.lastModifiedTime().toInstant, ZoneId.systemDefault()))
    s"""[
       |file = ${file.toString}
       |date = $date
       |size = ${attrs.size()}]""".stripMargin
  }

  def storeDirectoryContent(path: Path, exclude: Set[Path]): Task[(Option[(Path, Path)], Iterator[Path])] =
    if (exclude.contains(path)) Task((None, Iterator.empty))
    else IO.effect {
      val (dirs, files) = Files.list(path).iterator().asScala.partition(Files.isDirectory(_))
      if (files.nonEmpty) {
        val temp = Files.createTempFile(Paths.get("./temp"), "", ".txt")
        Files.writeString(temp, files.toSeq.sorted.map(makeFileDescription).mkString)
        (Some(path -> temp), dirs)
      } else (None, dirs)
    }

  def readDirectory(path: Path, exclude: Set[Path]): UIO[List[(Path, Path)]] =
    storeDirectoryContent(path, exclude).flatMap {
      case (temp, dirs) => ZIO.collectAllPar(dirs.map(readDirectory(_, exclude)).toList).map(_.flatten ++ temp.toList)
    }.catchAll(_ => ZIO.succeed(List.empty))

  def readDirectories(paths: List[Path], exclude: Set[Path]): UIO[List[(Path, Path)]] =
    ZIO.collectAllPar(paths.map(readDirectory(_, exclude))).map(_.flatten)

  def collapseFiles(target: Path, files: List[(Path, Path)]) =
    ZIO.effect {
      Files.deleteIfExists(target)
      val out = Files.createFile(target)
      files.sortBy(_._1).foreach { p =>
        Files.write(out, Files.readAllBytes(p._2), StandardOpenOption.APPEND)
      }
    }.catchAll(th => ZIO.effectTotal(th.printStackTrace()))

  def makeIndicator: ZIO[Console with Clock, Nothing, Unit] =
    Stream.iterate(1)(_ + 1).throttleShape(1, 6.seconds)(_ => 1)
      .tap(counter => console.putStr(if (counter % 10 == 0) "|" else ".")).runDrain

  override def run(args: List[String]) = {
    val (exclude, traverse) = args.partition(_.startsWith("-"))
    if (traverse.isEmpty)
      console.putStrLn("Program should be run with a list of directories to traverse and to exclude from " +
        "traversing using minus") *> ZIO.succeed(0)
    else
      for {
        indicator <- makeIndicator.fork
        dirs <- readDirectories(traverse.map(Paths.get(_).normalize().toAbsolutePath).filter(Files.isDirectory(_)),
          exclude.map(s => Paths.get(s.tail).normalize().toAbsolutePath).to(Set))
        _ <- collapseFiles(Paths.get("list.txt"), dirs)
        _ <- indicator.interrupt
      } yield 0
  }
}

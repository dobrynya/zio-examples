package com.gh.dobrynya.worm

import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{Files, Path, Paths, StandardOpenOption}
import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneId}
import zio._, console._
import zio.clock.Clock
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

  private def storeDirectoryContent(path: Path, exclude: Set[Path]): Task[(Option[(Path, Path)], Iterator[Path])] =
    if (exclude.contains(path)) ZIO.succeed((None, Iterator.empty))
    else Task {
      val (dirs, files) = Files.list(path).iterator().asScala.partition(Files.isDirectory(_))
      if (files.nonEmpty) {
        val temp = Files.createTempFile(Paths.get("./temp"), "", ".txt")
        Files.writeString(temp, files.toSeq.sorted.map(makeFileDescription).mkString)
        (Some(path -> temp), dirs)
      } else (None, dirs)
    }

  private def readDirectory(path: Path, exclude: Set[Path]): UIO[List[(Path, Path)]] =
    storeDirectoryContent(path, exclude).flatMap {
      case (temp, dirs) => ZIO.collectAllPar(dirs.map(readDirectory(_, exclude)).toList).map(_.flatten ++ temp.toList)
    }.catchAll(_ => ZIO.succeed(List.empty))

  private def readDirectories(paths: List[Path], exclude: Set[Path]): UIO[List[(Path, Path)]] =
    ZIO.foreachPar(paths)(readDirectory(_, exclude)).map(_.flatten)

  private def collapseFiles(target: Path, files: List[(Path, Path)]): UIO[Unit] =
    Task {
      Files.deleteIfExists(target)
      val out = Files.createFile(target)
      files.sortBy(_._1).foreach { p =>
        Files.write(out, Files.readAllBytes(p._2), StandardOpenOption.APPEND)
      }
    }.catchAll(th => ZIO.effectTotal(th.printStackTrace()))

  private def makeIndicator: ZIO[Console with Clock, Nothing, Unit] =
    (Stream.tick(6.seconds) &> Stream.iterate(1)(_ + 1))
      .tap(counter => console.putStr(if (counter % 10 == 0) "|" else "."))
      .runDrain
      .ignore

  override def run(args: List[String]) = {
    val (exclude, traverse) = args.partition(_.startsWith("-"))
    if (traverse.isEmpty)
      console.putStrLn("Program should be run with a list of directories to traverse and to exclude from " +
        "traversing using minus").exitCode
    else
      for {
        indicator <- makeIndicator.fork
        dirs <- readDirectories(traverse.map(Paths.get(_).normalize().toAbsolutePath).filter(Files.isDirectory(_)),
          exclude.map(s => Paths.get(s.tail).normalize().toAbsolutePath).to(Set))
        _ <- collapseFiles(Paths.get("list.txt"), dirs)
        _ <- indicator.interrupt
      } yield ExitCode.success
  }
}

package com.gh.dobrynya.worm

import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{Files, Path, Paths, StandardOpenOption}
import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneId}
import zio.*
import zio.stream.*
import scala.jdk.CollectionConverters.*

object DirectoryWorm extends ZIOAppDefault {
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
    else ZIO.attemptBlocking {
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

  private def readDirectories(paths: List[Path], exclude: Set[Path]) =
    Console.printLine(s"read directories $paths") *>
      ZIO.foreachPar(paths)(readDirectory(_, exclude)).map(_.flatten)

  private def collapseFiles(target: Path, files: List[(Path, Path)]): UIO[Unit] =
    ZIO.attemptBlocking {
      Files.deleteIfExists(target)
      val out = Files.createFile(target)
      files.sortBy(_._1).foreach { p =>
        Files.write(out, Files.readAllBytes(p._2), StandardOpenOption.APPEND)
      }
    }.catchAll(th => ZIO.succeed(th.printStackTrace()))

  private def makeIndicator: UIO[Unit] =
    (ZStream.tick(6.seconds) &> ZStream.iterate(1)(_ + 1))
      .tap(counter => Console.print(if (counter % 10 == 0) "|" else "."))
      .runDrain
      .ignore

  override def run =
    for {
      args <- ZIOAppArgs.getArgs
      (exclude, traverse) = args.toList.partition(_.startsWith("-"))
      _ <- Console.printLine(s"${traverse} directories should be traversed")
      _ <- Console.printLine("Program should be run with a list of directories to traverse and to exclude from " +
        "traversing using minus").when(traverse.isEmpty)
      _ <- runTraverse(traverse, exclude).when(traverse.nonEmpty)
    } yield ()

  private def runTraverse(traverse: List[String], exclude: List[String]) =
    for {
      indicator <- makeIndicator.fork
      dirs <- readDirectories(traverse.map(Paths.get(_).normalize().toAbsolutePath).filter(Files.isDirectory(_)),
        exclude.map(s => Paths.get(s.tail).normalize().toAbsolutePath).to(Set))
      _ <- collapseFiles(Paths.get("list.txt"), dirs)
      _ <- indicator.interrupt
    } yield ()
}

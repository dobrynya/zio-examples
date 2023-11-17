package com.gh.dobrynya.worm

import zio.*
import zio.stream.*
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.time.*
import java.time.format.DateTimeFormatter
import scala.jdk.CollectionConverters.*

object DirectoryWorm extends ZIOAppDefault:
  private val format = DateTimeFormatter.ISO_DATE_TIME

  private[worm] def makeFileDescription(file: Path) = 
    val attrs = Files.readAttributes(file, classOf[BasicFileAttributes])
    val date = format.format(LocalDateTime.ofInstant(attrs.lastModifiedTime().toInstant, ZoneId.systemDefault()))
    s"""[
       |file = ${file.toString}
       |date = $date
       |size = ${attrs.size()}]""".stripMargin

  private def storeDirectoryContent(path: Path, exclude: Set[Path]): Task[(Option[(Path, Path)], Iterator[Path])] =
    if exclude.contains(path) then ZIO.succeed((None, Iterator.empty))
    else ZIO.attemptBlocking {
      val (dirs, files) = Files.list(path).iterator().asScala.partition(Files.isDirectory(_))
      if (files.nonEmpty) {
        val temp = Files.createTempFile(Paths.get("./temp"), "", ".txt")
        Files.writeString(temp, files.toSeq.sorted.map(makeFileDescription).mkString)
        (Some(path -> temp), dirs)
      } else (None, dirs)
    }

  private def readDirectory(path: Path, exclude: Set[Path]): UIO[List[(Path, Path)]] =
    storeDirectoryContent(path, exclude)
      .flatMap((temp, dirs) => ZIO.collectAllPar(dirs.map(readDirectory(_, exclude)).toList)
        .map(_.flatten ++ temp.toList))
      .catchAll(_ => ZIO.succeed(List.empty))

  private def readDirectories(paths: List[Path], exclude: Set[Path]): IO[IOException, List[(Path, Path)]] =
    Console.printLine(s"read directories $paths") *>
      ZIO.foreachPar(paths)(readDirectory(_, exclude)).map(_.flatten)

  private def collapseFiles(target: Path, files: List[(Path, Path)]): UIO[Unit] =
    ZIO.attemptBlocking {
      Files.deleteIfExists(target)
      val out = Files.createFile(target)
      files.sortBy(_._1).foreach(p => Files.write(out, Files.readAllBytes(p._2), StandardOpenOption.APPEND))
    }.catchAll(th => ZIO.succeed(th.printStackTrace()))

  private def makeIndicator: UIO[Unit] =
    ZStream.iterate(1)(_ + 1).schedule(Schedule.spaced(6.seconds))
      .tap(counter => Console.print(if (counter % 10 == 0) "|" else "."))
      .runDrain.fork.unit

  override def run: ZIO[ZIOAppArgs, IOException, Unit] =
    for {
      args <- ZIOAppArgs.getArgs
      (exclude, traverse) = args.toList.partition(_.startsWith("-"))
      _ <- Console.printLine(if (traverse.nonEmpty) s"Directories ${traverse.mkString(",")} should be traversed"
      else "Program should be run with a list of directories to traverse and to exclude from traversing using minus")
      _ <- runTraverse(traverse, exclude).when(traverse.nonEmpty)
    } yield ()

  private def runTraverse(traverse: List[String], exclude: List[String]) =
    makeIndicator *>
      readDirectories(traverse.map(Paths.get(_).normalize().toAbsolutePath).filter(Files.isDirectory(_)),
        exclude.map(s => Paths.get(s.tail).normalize().toAbsolutePath).to(Set))
        .flatMap(dirs => collapseFiles(Paths.get("list.txt"), dirs))

package com.gh.dobrynya.md5checker

import java.io.IOException
import java.security.MessageDigest
import com.gh.dobrynya.http.*
import zio.*
import stream.*

object Md5Checker extends ZIOAppDefault :
  private[md5checker] def readFileDescriptions(url: String): ZIO[Console & HttpClient, IOException, Chunk[FileDescription]] =
    (download(url) >>> ZPipeline.utf8Decode >>> ZPipeline.splitLines).map(FileDescription.of).runCollect

  private[md5checker] def md5Hash[R] =
    ZSink.foldLeftChunks(MessageDigest.getInstance("MD5")) { (hasher, chunk: Chunk[Byte]) =>
      hasher.update(chunk.toArray)
      hasher
    }.map(_.digest().foldLeft("")((acc, byte) => s"$acc${String.format("%02x", byte)}"))

  private[md5checker] def printInfo(d: FileDescription) =
    d match {
      case FileDescription(url, md5, Some(calculatedMd5), None) if md5 != calculatedMd5 =>
        s"File at $url with hash $md5, calculated hash $calculatedMd5 does not conform the provided MD5 hash"
      case FileDescription(url, md5, Some(_), None) =>
        s"File at $url with hash $md5, calculated hash is equal provided hash"
      case FileDescription(url, md5, _, Some(error)) =>
        s"There is an error '$error' during processing a file at $url with provided hash $md5"
    }

  private[md5checker] def calculateMd5(description: FileDescription): ZIO[Console with HttpClient, IOException, Unit] =
    (if (description.valid)
      download(description.url).run(md5Hash).map(md5 => description.copy(calculatedMd5 = Some(md5)))
        .catchAll(th => Console.printLine(s"An error occurred $th!")
          .as(description.copy(error = Some(s"Error: ${th.getMessage}"))))
    else ZIO.succeed(description))
      .map(printInfo).forEachZIO(Console.printLine(_)).unit

  private[md5checker] def program(url: String): ZIO[Console with HttpClient, IOException, Unit] =
    readFileDescriptions(url).tap(files => Console.printLine(s"It needs to check the following files $files"))
      .flatMap(ZIO.foreachParDiscard(_)(calculateMd5))

  override def run =
    for {
      args <- ZIOAppArgs.getArgs
      _ <- Console.printLine("File list URL has not been specified, using default file:urls.txt").when(args.isEmpty)
      _ <- program(args.headOption.getOrElse("file:urls.txt")).provideCustom(HttpClient.live)
    } yield ()

package com.gh.dobrynya.md5checker

import scala.io.Source
import java.security.MessageDigest
import zio._
import console._
import stream._
import blocking.Blocking
import logging.slf4j.{Slf4jLogger, logger}
import zio.logging.Logging

object Md5Checker extends App {
  val env = new HttpClient.Live with Blocking.Live with Console.Live with Slf4jLogger.Live {
    override def formatMessage(msg: String): ZIO[Any, Nothing, String] = ZIO.effectTotal(msg)
  }

  def readFileDescriptions(url: String): ZIO[HttpClient with Blocking with Logging[String], Throwable, List[String]] =
    for {
      content <-
        logger.info(s"Reading files URLs to check MD5 hash from $url") *>
          ZIO.accessM[HttpClient with Blocking with Logging[String]](_.httpClient.download(url).run(ZSink.utf8DecodeChunk))
          files = Source.fromString(content).getLines().toList
          _ <- logger.info(s"It needs to check the following files $files")
    } yield files

  def md5Hash[R] =
    ZSink.foldLeft[Chunk[Byte], MessageDigest](MessageDigest.getInstance("MD5")) { (hasher, chunk) =>
      hasher.update(chunk.toArray)
      hasher
    }.map(_.digest().foldLeft("")((acc, byte) => s"$acc${String.format("%02x", byte)}"))

  def printInfo(d: FileDescription) =
    d match {
      case FileDescription(url, md5, Some(calculatedMd5), None) if md5 != calculatedMd5 =>
        s"File at $url with hash $md5, calculated hash $calculatedMd5 does not conform the provided MD5 hash"
      case FileDescription(url, md5, Some(_), None) =>
        s"File at $url with hash $md5, calculated hash is equal provided hash"
      case FileDescription(url, md5, _, Some(error)) =>
        s"There is an error '$error' during processing a file at $url with provided hash $md5"
    }

  def calculateMd5(description: FileDescription) =
    for {
      http <- ZIO.access[HttpClient](_.httpClient)
      line <- (if (description.valid) http.download(description.url).run(md5Hash)
        .map(md5 => description.copy(calculatedMd5 = Some(md5)))
        .catchAll(th =>
          logger.error(s"An error occurred $th!", Cause.fail(th)) *>
            ZIO.effectTotal(description.copy(error = Some(s"Error: ${th.getMessage}"))))
      else ZIO.succeed(description)).map(printInfo)
      _ <- putStrLn(line)
    } yield line

  val url = "file:urls.txt"

  val program =
    for {
      list <- readFileDescriptions(url).map(_.map(FileDescription.of))
      _ <- ZIO.collectAllParN(4)(list.map(calculateMd5))
    } yield ()

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] =
    program.provide(env).catchAll(th => ZIO.effectTotal(th.printStackTrace())).as(0)
}

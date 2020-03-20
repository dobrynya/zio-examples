package com.gh.dobrynya.md5checker

import java.security.MessageDigest
import zio._
import console._
import stream._
import blocking.Blocking
import com.gh.dobrynya.md5checker.http.HttpClient

object Md5Checker extends App {
  type MyEnv = HttpClient with Blocking with Console

  def bytes2strings[R, E](bytes: ZStream[R, E, Chunk[Byte]]): ZStream[R, E, String] =
    ZStreamChunk(bytes.transduce[R, E, Chunk[Byte], String](Sink.utf8DecodeChunk).transduce(Sink.splitLines))
      .flattenChunks

  def readFileDescriptions(url: String): ZIO[MyEnv, Throwable, List[String]] =
    for {
      _ <- console.putStrLn(s"Reading files URLs to check MD5 hash from $url")
      files <- ZIO.accessM[HttpClient](r => bytes2strings(r.get.download(url)).runCollect)
      _ <- console.putStrLn(s"It needs to check the following files $files")
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

  def calculateMd5(description: FileDescription): RIO[MyEnv, String] =
    for {
      http <- ZIO.access[HttpClient](_.get)
      line <- (if (description.valid) http.download(description.url).run(md5Hash)
        .map(md5 => description.copy(calculatedMd5 = Some(md5)))
        .catchAll(th =>
          console.putStrLn(s"An error occurred $th!") *>
            ZIO.effectTotal(description.copy(error = Some(s"Error: ${th.getMessage}"))))
      else ZIO.succeed(description)).map(printInfo)
      _ <- putStrLn(line)
    } yield line

  def program(url: String) =
    for {
      list <- readFileDescriptions(url).map(_.map(FileDescription.of))
      _ <- ZIO.collectAllParN(4)(list.map(calculateMd5))
    } yield ()

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] =
    ZIO.when(args.isEmpty)(console.putStrLn("File list URL has not been specified, using default")) *>
      program(args.headOption.getOrElse("file:urls.txt")).provideCustomLayer(HttpClient.live).fold(_ => 0, _ => 0)
}

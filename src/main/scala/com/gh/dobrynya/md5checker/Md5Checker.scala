package com.gh.dobrynya.md5checker

import java.security.MessageDigest
import com.gh.dobrynya.http
import com.gh.dobrynya.http.HttpClient
import zio._, stream._

object Md5Checker extends App {
  def bytes2strings[R, E](bytes: ZStream[R, E, Byte]): ZStream[R, E, String] =
    bytes.transduce(ZTransducer.utf8Decode).transduce(ZTransducer.splitLines)

  def readFileDescriptions(url: String) =
    for {
      _ <- console.putStrLn(s"Reading files URLs to check MD5 hash from $url")
      files <- http.download(url).via(bytes2strings[HttpClient, Throwable]).runCollect
      _ <- console.putStrLn(s"It needs to check the following files $files")
    } yield files

  def md5Hash[R] =
    ZSink.foldLeftChunks(MessageDigest.getInstance("MD5")) { (hasher, chunk: Chunk[Byte]) =>
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
      line <- (if (description.valid) http.download(description.url).run(md5Hash)
        .map(md5 => description.copy(calculatedMd5 = Some(md5)))
        .catchAll(th =>
          console.putStrLn(s"An error occurred $th!") *>
            ZIO.effectTotal(description.copy(error = Some(s"Error: ${th.getMessage}"))))
      else ZIO.succeed(description)).map(printInfo)
      _ <- console.putStrLn(line)
    } yield line

  def program(url: String) =
    for {
      list <- readFileDescriptions(url).map(_.map(FileDescription.of))
      _ <- ZIO.collectAllPar(list.map(calculateMd5))
    } yield ()

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] =
    ZIO.when(args.isEmpty)(console.putStrLn("File list URL has not been specified, using default")) *>
      program(args.headOption.getOrElse("file:urls.txt")).provideSomeLayer(HttpClient.live).fold(_ => 1, _ => 0)
}

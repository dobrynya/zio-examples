package com.gh.dobrynya.md5checker

import zio.*
import zio.stream.*
import zio.http.{Client, Request}
import java.security.MessageDigest
import java.util.HexFormat

object Md5Checker extends ZIOAppDefault :
  private[md5checker] val md5 =
    ZSink.digest(MessageDigest.getInstance("MD5")).map(bytes => HexFormat.of.formatHex(bytes.toArray))

  private[md5checker] def download(url: String) =
    if url.startsWith("file:") then ZStream.fromFileName(url.drop(5))
    else Client.streamingWith(Request.get(url))(_.body.asStream)

  private[md5checker] def readFileDescriptions(url: String) = 
    download(url).via(ZPipeline.utf8Decode >>> ZPipeline.splitLines).map(FileDescription.parse)

  private[md5checker] def formatOutput(d: FileDescription) =
    d match
      case FileDescription(url, md5, Some(calculatedMd5), None) =>
        s"File at $url with hash $md5, calculated hash $calculatedMd5 is${if md5 != calculatedMd5 then " not " else " "}equal provided hash"
      case FileDescription(url, _, _, Some(error)) =>
        s"File at $url can't be downloaded due to an error: $error!"
  
  private[md5checker] def calculateMd5(description: FileDescription) =
      download(description.url).run(md5).map(md5 => description.copy(calculatedMd5 = Some(md5)))
        .catchAll(th => ZIO.succeed(description.copy(error = Some(th.getMessage))))

  private[md5checker] def program(url: String) =
      readFileDescriptions(url).partition(_.valid) 
        .flatMap(_.mapZIOParUnordered(8)(calculateMd5).merge(_).foreach(file => Console.printLine(formatOutput(file))))

  override def run: ZIO[ZIOAppArgs & Scope, Throwable, Unit] =
    ZIOAppArgs.getArgs.flatMap(args => program(args.headOption.getOrElse("file:urls.txt")))
      .provideSome[ZIOAppArgs & Scope](Client.default)

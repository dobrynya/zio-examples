package com.gh.dobrynya.md5checker

import java.net.URL
import scala.io.Source
import java.security.MessageDigest
import zio._, console._, stream._, blocking.{Blocking, effectBlocking}

object Md5Checker extends App {
  val env = new HttpClient.Live with Blocking.Live with Console.Live

  def readFileDescriptions(url: String) =
    ZIO.accessM[HttpClient with Blocking](_.httpClient.download(url).run(ZSink.utf8DecodeChunk))
      .map(Source.fromString(_).getLines().toList)

  def md5Hash[R] =
    ZSink.foldLeft[Chunk[Byte], MessageDigest](MessageDigest.getInstance("MD5")) { (hasher, chunk) =>
      hasher.update(chunk.toArray)
      hasher
    }.map(_.digest().foldLeft("")((acc, byte) => s"$acc${String.format("%02x", byte)}"))

  def printInfo(d: FileDescription) =
    d match {
      case FileDescription(url, md5, Some(calculatedMd5), None) if md5 != calculatedMd5 =>
        s"File at $url with hash $md5, calculated hash $calculatedMd5 does not conform the provided MD5 hash"
      case FileDescription(url, md5, Some(calculatedMd5), None) =>
        s"File at $url with hash $md5, calculated hash is equal provided hash"
      case FileDescription(url, md5, _, Some(error)) =>
        s"There is an error '$error' during processing a file at $url with provided hash $md5"
    }

  def calculateMd5(description: FileDescription) =
    for {
      http <- ZIO.access[HttpClient](_.httpClient)
      _ <- (if (description.valid) http.download(description.url).run(md5Hash)
        .map(md5 => description.copy(calculatedMd5 = Some(md5)))
        .catchAll(th => ZIO.effectTotal(description.copy(error = Some(s"Error: ${th.getMessage}"))))
      else ZIO.succeed(description)).map(printInfo) >>= putStrLn
    } yield ()

  val url = "file:urls.txt"

  val program =
    for {
      _ <- putStrLn(s"Downloading a file list from $url")
      list <- readFileDescriptions(url).map(_.map(FileDescription.of))
      _ <- ZIO.collectAllParN(4)(list.map(calculateMd5))
    } yield ()

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] =
    program.provide(env).catchAll(th => ZIO.effectTotal(th.printStackTrace())).as(0)
}

case class FileDescription(url: String, md5: String, calculatedMd5: Option[String] = None, error: Option[String] = None) {
  def valid: Boolean = error.isEmpty
}

case object FileDescription {
  private val pattern = "(.+),(.+)".r

  def of(raw: String) = raw match {
    case pattern(url, md5) => FileDescription(url, md5)
    case _ => FileDescription("", "", error = Some(s"Invalid data: $raw!"))
  }
}

trait HttpClient {
  def httpClient: HttpClient.Service
}

object HttpClient {

  trait Service {
    def download(url: String): ZStream[Blocking, Throwable, Chunk[Byte]]
  }

  trait Live extends HttpClient {
    override def httpClient: Service = (url: String) =>
      ZStream.managed(ZManaged.make(effectBlocking(new URL(url).openStream()))(is => effectBlocking(is.close()).ignore))
        .flatMap(ZStream.fromInputStream(_).chunks)
  }

}
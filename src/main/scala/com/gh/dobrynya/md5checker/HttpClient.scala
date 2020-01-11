package com.gh.dobrynya.md5checker

import zio._
import console._
import zio.blocking._
import zio.stream.ZStream
import zio.interop.catz._
import zio.interop.catz.core._
import zio.interop.catz.implicits._
import cats.effect._
import java.net.URL

import zio.logging.Logging
import org.http4s.Method._
import org.http4s.{Response, Uri}
import org.http4s.client._
import org.http4s.client.dsl.io._
import org.http4s.client.blaze.BlazeClientBuilder

trait HttpClient {
  def httpClient: HttpClient.Service
}

object HttpClient {
  trait Service {
    def download(url: String): ZStream[Blocking with Logging[String], Throwable, Chunk[Byte]]
  }

  trait Live extends HttpClient {
    override def httpClient: Service = (url: String) =>
      ZStream.managed(ZManaged.make(
        logging.slf4j.logger.info(s"Downloading a file from $url") *>
          effectBlocking(new URL(url).openStream())
            .tapError(th =>
              logging.slf4j.logger.info(s"An error occurred: ${th.getMessage}!")))(is => effectBlocking(is.close()).ignore)
      ).flatMap(ZStream.fromInputStream(_).chunks)
  }
}

object BlazeHttpClient {
  def client =
    ZIO.runtime.map { implicit r: Runtime[Any] =>
      BlazeClientBuilder[Task](r.platform.executor.asEC).resource
    }
}

object TestBlazeHttpClient extends App {
  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] =
    for {
      c <- BlazeHttpClient.client
      _ <- c.use(download("https://mail.ru"))
        .flatMap(putStrLn)
        .catchAll(th => ZIO.effectTotal(th.printStackTrace()))
    } yield 0


  def download(url: String)(c: Client[Task]) = c.expect[String](url)
}

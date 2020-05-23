package com.gh.dobrynya

import java.net.URL
import zio._, stream._
import zio.blocking.Blocking
import zio.console.Console

package object http {
  type HttpClient = Has[HttpClient.Service]

  def download(url: String): ZStream[HttpClient, Throwable, Byte] =
    ZStream.unwrap(ZIO.access[HttpClient](_.get.download(url)))

  object HttpClient {
    trait Service {
      def download(url: String): Stream[Throwable, Byte]
    }

    val live: ZLayer[Console with Blocking, Nothing, HttpClient] =
      ZLayer.fromServices[Console.Service, Blocking.Service, HttpClient.Service]((console, blocking) =>
        (url: String) => Stream.fromInputStreamEffect(
          console.putStrLn(s"Downloading a file from $url") *>
            Task(new URL(url).openStream()).refineToOrDie)
          .provideLayer(ZLayer.succeed(blocking))
      )
  }
}
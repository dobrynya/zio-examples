package com.gh.dobrynya

import java.net.URL
import zio._, stream._
import zio.blocking.Blocking
import zio.console.Console

package object http {
  type HttpClient = Has[HttpClient.Service]

  def download(url: String): ZStream[HttpClient, Throwable, Chunk[Byte]] =
    ZStream.unwrap(ZIO.access[HttpClient](_.get.download(url)))

  object HttpClient {
    trait Service {
      def download(url: String): Stream[Throwable, Chunk[Byte]]
    }

    val live: ZLayer[Console with Blocking, Nothing, HttpClient] =
      ZLayer.fromServices[Console.Service, Blocking.Service, HttpClient.Service]((console, blocking) =>
        new Service {
          private def showError(th: Throwable) =
            console.putStrLn(s"An error occurred: ${th.getMessage}!")

          override def download(url: String) =
            Stream.managed(ZManaged.make(
              console.putStrLn(s"Downloading a file from $url") *>
                blocking.effectBlocking(new URL(url).openStream())
                  .tapError(showError))(is => blocking.effectBlocking(is.close()).ignore)
            ).flatMap(Stream.fromInputStream(_).chunks)
        }
      )
  }
}
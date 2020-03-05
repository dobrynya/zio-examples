package com.gh.dobrynya.md5checker

import java.net.URL
import zio._
import zio.blocking.Blocking
import zio.console.Console
import zio.stream.Stream

package object http {
  type HttpClient = Has[HttpClient.Service]

  object HttpClient {
    trait Service {
      def download(url: String): Stream[Throwable, Chunk[Byte]]
    }

    lazy val live =
      ZLayer.fromServices((console: Console.Service, blocking: Blocking.Service) =>
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
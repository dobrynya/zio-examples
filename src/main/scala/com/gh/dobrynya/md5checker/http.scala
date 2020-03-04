package com.gh.dobrynya.md5checker

import java.net.URL
import zio._
import zio.blocking._
import zio.console.Console
import zio.stream.ZStream

package object http {
  type HttpClient = Has[HttpClient.Service]

  object HttpClient {
    trait Service {
      def download(url: String): ZStream[Blocking with Console, Throwable, Chunk[Byte]]
    }

    lazy val live: ZLayer[Console with Blocking, Nothing, HttpClient] = ZLayer.succeed(
      (url: String) => ZStream.managed(ZManaged.make(
        console.putStrLn(s"Downloading a file from $url") *>
          effectBlocking(new URL(url).openStream())
            .tapError(th =>
              console.putStrLn(s"An error occurred: ${th.getMessage}!")))(is => effectBlocking(is.close()).ignore)
      ).flatMap(ZStream.fromInputStream(_).chunks)
    )
  }
}
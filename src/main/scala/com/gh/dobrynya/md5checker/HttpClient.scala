package com.gh.dobrynya.md5checker

import java.net.URL

import zio.ZLayer.NoDeps
import zio._
import zio.blocking._
import zio.console.Console
import zio.stream.ZStream

trait HttpClient {
  def httpClient: HttpClient.Service
}

object HttpClient {
  trait Service {
    def download(url: String): ZStream[Blocking with Console, Throwable, Chunk[Byte]]
  }

  val live = ZLayer.succeed(
    new HttpClient {
      override def httpClient: Service = (url: String) =>
        ZStream.managed(ZManaged.make(
          console.putStrLn(s"Downloading a file from $url") *>
            effectBlocking(new URL(url).openStream())
              .tapError(th =>
                console.putStrLn(s"An error occurred: ${th.getMessage}!")))(is => effectBlocking(is.close()).ignore)
        ).flatMap(ZStream.fromInputStream(_).chunks)
    }
  )
}


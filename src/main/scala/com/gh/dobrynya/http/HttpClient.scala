package com.gh.dobrynya.http

import java.io.IOException
import java.net.URL
import zio._
import stream._
import zio.Console

trait HttpClient {
  def download(url: String): Stream[IOException, Byte]
}

def download(url: String): ZStream[HttpClient, IOException, Byte] = ZStream.serviceWithStream[HttpClient](_.download(url))

object HttpClient {
  private class HttpClientLive extends HttpClient {
    def download(url: String): Stream[IOException, Byte] =
      ZStream.fromInputStreamZIO(
        ZIO.logInfo(s"Downloading a file from $url") *>
          ZIO.attemptBlocking(new URL(url).openStream()).refineToOrDie)
  }

  val live: ULayer[HttpClient] = ZLayer.succeed(new HttpClientLive)
}
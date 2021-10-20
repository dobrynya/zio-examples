package com.gh.dobrynya.http

import java.io.IOException
import java.net.URL
import zio._
import stream._
import zio.blocking.Blocking
import zio.console.Console

trait HttpClient {
  def download(url: String): Stream[IOException, Byte]
}

object HttpClient {
  def download(url: String): ZStream[Has[HttpClient], IOException, Byte] =
    ZStream.accessStream[Has[HttpClient]](_.get.download(url))

  private class HttpClientLive(console: Console.Service, blocking: Blocking.Service) extends HttpClient {
    def download(url: String): Stream[IOException, Byte] =
      Stream.fromInputStreamEffect(
        console.putStrLn(s"Downloading a file from $url") *>
          Task(new URL(url).openStream()).refineToOrDie).provide(Has(blocking))
  }

  val live: URLayer[Console with Blocking, Has[HttpClient]] = (new HttpClientLive(_, _)).toLayer
}
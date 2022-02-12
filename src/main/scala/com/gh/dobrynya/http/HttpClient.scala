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
  private class HttpClientLive(console: Console) extends HttpClient {
    def download(url: String): Stream[IOException, Byte] =
      ZStream.fromInputStreamZIO(
        console.printLine(s"Downloading a file from $url") *>
          Task(new URL(url).openStream()).refineToOrDie)
  }

  val live: URLayer[Console, HttpClient] = (new HttpClientLive(_)).toLayer
}
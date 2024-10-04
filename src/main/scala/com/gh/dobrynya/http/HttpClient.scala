package com.gh.dobrynya.http

import zio.*
import zio.stream.*
import java.net.URI

def download(url: String): ZStream[HttpClient, Exception, Byte] = ZStream.serviceWithStream[HttpClient](_.download(url))

trait HttpClient:
  def download(url: String): Stream[Exception, Byte]

object HttpClient:
  private class HttpClientLive extends HttpClient:
    def download(url: String): Stream[Exception, Byte] = 
      ZStream.blocking:
        ZStream.fromInputStreamZIO(ZIO.attemptBlocking(URI.create(url).toURL.openStream()).refineToOrDie)

  val jdkClient: ULayer[HttpClient] = ZLayer.succeed(HttpClientLive())

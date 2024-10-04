package com.gh.dobrynya.http

import zio.*
import zio.http.*
import zio.stream.*

class ZioHttpClient(client: Client, default: HttpClient) extends HttpClient:
  override def download(url: String): Stream[Exception, Byte] =
    if url.startsWith("file:") then default.download(url) else
      client.stream(Request.get(url))(_.body.asStream).refineToOrDie

val zioHttpClient: Layer[Throwable, ZioHttpClient] =
  (Client.default ++ HttpClient.jdkClient) >>> ZLayer.derive[ZioHttpClient]

package com.gh.dobrynya

import zio.*, stream.*

object QueueTests extends App {
  override def run(args: List[String]) =
    (
      for {
        q <- Queue.unbounded[String]
        receiver <-
          ZStream.fromQueue(q).tap(s => Console.printLine(s"Received: $s"))
          .runDrain
//          .catchAllCause(c => UIO(println(s"Interrupted: ${c.interrupted}\n${c.prettyPrint}")))
          .fork
        _ <- q.offerAll(List("One", "two", "three")) *>
          Clock.sleep(300.millis) *> q.shutdown *> q.awaitShutdown
        _ <- receiver.join.flatMap(s => Console.printLine(s"Result ${s}"))
        _ <- ZIO.suspend(ZIO.succeed(10))
      } yield ()
      ).exitCode
}

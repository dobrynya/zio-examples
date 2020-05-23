package com.gh.dobrynya.md5checker

import com.gh.dobrynya.md5checker.Md5Checker._
import zio.test._
import zio.stream._
import Assertion._
import com.gh.dobrynya.http.HttpClient
import zio.{Chunk, ZIO}
import zio.test.TestAspect._
import zio.test.junit.JUnitRunnableSpec
import scala.io.Source

//noinspection SourceNotClosed
class Md5CheckerTest extends JUnitRunnableSpec {
  override def spec =
    suite("Md5 checker tests")(
      testM("readFileDescriptions should fail when reading a wrong URL")(
        assertM(readFileDescriptions("file:non-existent-file").provideCustomLayer(HttpClient.live))(anything)
      ) @@ failing,
      testM("readFileDescriptions should read a file successfully")(
        for {
          expected <- ZIO.effect(Source.fromFile("urls.txt").getLines().toList)
          actual <- readFileDescriptions("file:urls.txt").provideCustomLayer(HttpClient.live)
        } yield assert(actual)(equalTo(expected))
      ),
      testM("Calculating a hash for a string should succeed")(
        assertM(
          Stream.fromIterable("just a string to calculate its MD5 hash".getBytes())
            .run(md5Hash))(equalTo("9a3bd129258e40744133e4a38e5ab99d")
        )
      ),
      testM("Calculating a hash for a concrete file should succeed")(
        assertM(
          calculateMd5(FileDescription("file:gradle/wrapper/gradle-wrapper.jar",
            "ae4eb03f944bce8d3abe03b82bdbca35")).provideCustomLayer(HttpClient.live))(
          containsString("file:gradle/wrapper/gradle-wrapper.jar") &&
            containsString("ae4eb03f944bce8d3abe03b82bdbca35")
        )
      )
    )
}

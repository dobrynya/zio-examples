package com.gh.dobrynya.md5checker

import com.gh.dobrynya.md5checker.Md5Checker._
import zio.test._
import zio.stream._
import Assertion._
import com.gh.dobrynya.http.HttpClient
import zio.*
import zio.test.TestAspect._
import scala.io.Source

object Md5CheckerTest extends ZIOSpecDefault {
  override def spec =
    suite("Md5 checker tests")(
      test("readFileDescriptions should fail when reading a wrong URL")(
        assertM(readFileDescriptions("file:non-existent-file").runDrain)(anything)
      ) @@ failing,
      test("readFileDescriptions should read a file successfully")(
        for {
          expected <- ZIO.attempt(Source.fromFile("urls.txt").getLines().toList.map(FileDescription.of))
          actual <- readFileDescriptions("file:urls.txt").runCollect
        } yield assert(actual.toList)(equalTo(expected))
      ),
      test("Calculating a hash for a string should succeed")(
        assertM(
          Stream.fromIterable("just a string to calculate its MD5 hash".getBytes())
            .run(md5Hash))(equalTo("9a3bd129258e40744133e4a38e5ab99d")
        )
      ),
      test("Calculating a hash for a concrete file should succeed")(
        assertM(
          for {
            _ <- calculateMd5(FileDescription("file:gradle/wrapper/gradle-wrapper.jar",
              "ae4eb03f944bce8d3abe03b82bdbca35"))
            c <- TestConsole.output
          } yield c.mkString
        )(
          containsString("file:gradle/wrapper/gradle-wrapper.jar") &&
            containsString("ae4eb03f944bce8d3abe03b82bdbca35")
        )
      )
    ).provideCustom(HttpClient.live)
}

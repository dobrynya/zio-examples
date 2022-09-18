package com.gh.dobrynya.md5checker

import com.gh.dobrynya.md5checker.Md5Checker.*
import zio.test.*
import zio.stream.*
import Assertion.*
import com.gh.dobrynya.http.*
import zio.*
import zio.test.TestAspect.*
import java.io.File
import scala.io.Source

object Md5CheckerTest extends ZIOSpecDefault {
  override def spec =
    suite("Md5 checker tests")(
      test("readFileDescriptions should fail when reading a wrong URL")(
        assertZIO(readFileDescriptions("file:non-existent-file").runDrain)(anything)
      ) @@ failing,
      test("readFileDescriptions should read a file successfully")(
        for {
          expected <- ZStream.fromFile(new File("urls.txt"))
            .via(ZPipeline.utf8Decode >>> ZPipeline.splitLines)
            .map(FileDescription.of)
            .runCollect
          actual <- readFileDescriptions("file:urls.txt").runCollect
        } yield assertTrue(actual == expected)
      ),
      test("Calculating a hash for a string should succeed")(
        assertZIO(
          ZStream.fromIterable("just a string to calculate its MD5 hash".getBytes())
            .run(md5Hash))(equalTo("9a3bd129258e40744133e4a38e5ab99d")
        )
      ),
      test("Calculating a hash for a concrete file should succeed")(
        assertZIO(
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
    ).provide(HttpClient.live)
}

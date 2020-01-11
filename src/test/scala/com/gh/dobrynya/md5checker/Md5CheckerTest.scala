package com.gh.dobrynya.md5checker

import java.nio.charset.StandardCharsets
import com.gh.dobrynya.md5checker.Md5Checker._
import zio.test._
import zio.stream._
import Assertion._
import zio.{Chunk, ZIO}
import zio.test.TestAspect._
import scala.io.Source

object Md5CheckerTest extends DefaultRunnableSpec(
  suite("Md5Checker tests")(
    testM("readFileDescriptions should fail when reading a wrong URL")(
      assertM(readFileDescriptions("file:non-existent-file").provide(env), anything)
    ) @@ failure,
    testM("readFileDescriptions should read a file successfully")(
      for {
        expected <- ZIO.effect(Source.fromFile("urls.txt").getLines().toList)
        actual <- readFileDescriptions("file:urls.txt").provide(env)
      } yield assert(actual, equalTo(expected))
    ),
    testM("Calculating a hash for a string should succeed")(
      assertM(
        Stream.apply("just a string to calculate its MD5 hash")
          .map { s => Chunk.fromArray(s.getBytes(StandardCharsets.UTF_8)) }
          .run(md5Hash), equalTo("9a3bd129258e40744133e4a38e5ab99d")
      )
    ),
    testM("Calculating a hash for a concrete file should succeed")(
      assertM(
        calculateMd5(FileDescription("file:gradle/wrapper/gradle-wrapper.jar",
          "ae4eb03f944bce8d3abe03b82bdbca35")).provide(env),
        containsString("file:gradle/wrapper/gradle-wrapper.jar") &&
          containsString("ae4eb03f944bce8d3abe03b82bdbca35")
      )
    )
  )
)

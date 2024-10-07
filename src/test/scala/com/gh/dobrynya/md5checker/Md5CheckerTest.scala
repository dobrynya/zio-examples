package com.gh.dobrynya.md5checker

import com.gh.dobrynya.md5checker.Md5Checker.*
import zio.*
import zio.http.Client
import zio.stream.*
import zio.test.*
import zio.test.Assertion.*
import zio.test.TestAspect.*

object Md5CheckerTest extends ZIOSpecDefault:
  override def spec: Spec[TestEnvironment & Scope, Throwable] =
    suite("Md5 Checker tests")(
      test("readFileDescriptions should fail when reading a wrong URL"):
        assertZIO(readFileDescriptions("file:non-existent-file").runDrain.exit)(failsWithA[Exception])
      ,
      test("readFileDescriptions should read a file successfully"):
        for
          expected <- 
            ZStream.fromFileName("urls.txt").via(ZPipeline.utf8Decode >>> ZPipeline.splitLines)
              .map(FileDescription.parse).runCollect
          actual <- readFileDescriptions("file:urls.txt").runCollect
        yield assertTrue(actual == expected)
      ,
      test("Calculating a hash for a string should succeed"):
        for result <- ZStream.fromIterable("just a string to calculate its MD5 hash".getBytes()).run(md5)
        yield assertTrue(result == "9a3bd129258e40744133e4a38e5ab99d")
      ,
      test("Calculating a hash for a concrete file should succeed"):
        for result <- calculateMd5(FileDescription("file:build.sbt", "abfcd7e74e12c37cc9b50f45fd77ca11"))
        yield assertTrue(result.calculatedMd5.contains("abfcd7e74e12c37cc9b50f45fd77ca11"))
    ).provideSome[Scope](Client.default)

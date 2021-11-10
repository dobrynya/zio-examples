package com.gh.dobrynya.worm

import java.nio.file.Paths
import DirectoryWorm._
import zio.test._
import zio.test.Assertion._

class DirectoryWormTest extends DefaultRunnableSpec {
  override def spec =
    suite("Directory Worm test suite")(
      test("Make file description should include all required attributes") {
        assert(makeFileDescription(Paths.get("build.gradle")))(containsString("build.gradle"))
      }
    )
}

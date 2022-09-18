lazy val zioExamples =
  project.in(file("."))
    .settings(
      name := "zio-examples",
      organization := "io.github.dobrynya",
      version := "0.0.1",
      scalaVersion := "3.2.0",
      testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
      libraryDependencies ++= Seq(
        "dev.zio" %% "zio-streams" % "2.0.2",
        "dev.zio" %% "zio-test" % "2.0.2" % Test,
        "dev.zio" %% "zio-test-sbt" % "2.0.2" % Test
      )
    )

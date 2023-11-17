lazy val zioExamples = project
  .in(file("."))
  .settings(
    name := "zio-examples",
    organization := "io.github.dobrynya",
    version := "0.0.1",
    scalaVersion := "3.3.1",
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-streams" % "2.0.19",
      "dev.zio" %% "zio-test" % "2.0.19" % Test,
      "dev.zio" %% "zio-test-sbt" % "2.0.19" % Test
    )
  )

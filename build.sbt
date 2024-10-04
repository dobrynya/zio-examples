lazy val zioExamples = project
  .in(file("."))
  .settings(
    name := "zio-examples",
    organization := "io.github.dobrynya",
    version := "0.0.1",
    scalaVersion := "3.5.1",
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-streams" % "2.1.9",
      "dev.zio" %% "zio-http" % "3.0.1",
      "dev.zio" %% "zio-test" % "2.1.9" % Test,
      "dev.zio" %% "zio-test-sbt" % "2.1.9" % Test
    )
  )

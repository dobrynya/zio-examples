ThisBuild / name := "zio-examples"
ThisBuild / organization := "io.github.dobrynya"
ThisBuild / scalaVersion := "3.0.2"

ThisBuild / libraryDependencies ++= Seq(
  "dev.zio" %% "zio-streams" % "1.0.12",
  "dev.zio" %% "zio-test" % "1.0.12" % Test,
  "dev.zio" %% "zio-test-sbt" % "1.0.12" % Test
)

ThisBuild / testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")

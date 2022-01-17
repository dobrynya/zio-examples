import com.typesafe.sbt.packager

lazy val zioExamples =
  project.in(file("."))
    .settings(
      name := "zio-examples",
      organization := "io.github.dobrynya",
      version := "0.0.1",
      scalaVersion := "3.1.0",
      Compile / mainClass := Some("com.gh.dobrynya.md5checker.Md5Checker"),
      testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
      libraryDependencies ++= Seq(
        "dev.zio" %% "zio-streams" % "2.0.0-RC1",
        "dev.zio" %% "zio-test" % "2.0.0-RC1" % Test,
        "dev.zio" %% "zio-test-sbt" % "2.0.0-RC1" % Test
      ),
//      Docker / packageName  := "docker-test"
      Docker / dockerEntrypoint  := Seq("md-5-checker")
    )
    .enablePlugins(JavaServerAppPackaging, GraalVMNativeImagePlugin)

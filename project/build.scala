import sbt._
import Keys._

object build extends Build {
  lazy val sharedSettings = Defaults.defaultSettings ++ Seq(
    scalaVersion := "2.11.7",
    crossVersion := CrossVersion.full,
    version := "2.1.0-SNAPSHOT",
    organization := "jp.ac.u_tokyo.i.ci.csg.hiroshi_yamaguchi",
    description := "Empowers production Scala compiler with latest macro developments + inverse macros",
    resolvers += Resolver.sonatypeRepo("snapshots"),
    resolvers += Resolver.sonatypeRepo("releases"),
    publishMavenStyle := true,
    publishArtifact in Test := false,
    scalacOptions ++= Seq("-deprecation", "-feature"),
    javacOptions  ++= Seq("-source", "1.8", "-target", "1.8"),
    parallelExecution in Test := false, // hello, reflection sync!!
    logBuffered := false,
    scalaHome := {
      val scalaHome = System.getProperty("paradise.scala.home")
      if (scalaHome != null) {
        println(s"Going for custom scala home at $scalaHome")
        Some(file(scalaHome))
      } else None
    }
  )


  lazy val plugin = Project(
    id   = "paradise",
    base = file("plugin")
  ) settings (
    sharedSettings : _*
  ) settings (
    resourceDirectory in Compile <<= baseDirectory(_ / "src" / "main" / "scala" / "org" / "scalamacros" / "paradise" / "embedded"),
    libraryDependencies <+= (scalaVersion)("org.scala-lang" % "scala-library" % _),
    libraryDependencies <+= (scalaVersion)("org.scala-lang" % "scala-reflect" % _),
    libraryDependencies <+= (scalaVersion)("org.scala-lang" % "scala-compiler" % _)
  )

  lazy val usePluginSettings = Seq(
    scalacOptions in Compile <++= (Keys.`package` in (plugin, Compile)) map { (jar: File) =>
      System.setProperty("sbt.paths.plugin.jar", jar.getAbsolutePath)
      val addPlugin = "-Xplugin:" + jar.getAbsolutePath
      // Thanks Jason for this cool idea (taken from https://github.com/retronym/boxer)
      // add plugin timestamp to compiler options to trigger recompile of
      // main after editing the plugin. (Otherwise a 'clean' is needed.)
      val dummy = "-Jdummy=" + jar.lastModified
      Seq(addPlugin, dummy)
    }
  )

  lazy val sandbox = Project(
    id   = "sandbox",
    base = file("sandbox")
  ) settings (
    sharedSettings ++ usePluginSettings: _*
  ) settings (
    libraryDependencies <+= (scalaVersion)("org.scala-lang" % "scala-reflect" % _),
    publishArtifact in Compile := false
  )

  lazy val library = Project(
    id = "library",
    base = file("library")
  ) settings (
    sharedSettings: _*
  ) settings (
    libraryDependencies <+= (scalaVersion)("org.scala-lang" % "scala-reflect" % _),
    // macroAnnotation に macro フラグを立てるためオリジナルのプラグインを活用
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0-M5" cross CrossVersion.full),
    publishArtifact in Compile := false
  )

  lazy val tests = Project(
    id   = "tests",
    base = file("tests")
  ) settings (
    sharedSettings ++ usePluginSettings: _*
  ) settings (
    libraryDependencies <+= (scalaVersion)("org.scala-lang" % "scala-reflect" % _),
    libraryDependencies <+= (scalaVersion)("org.scala-lang" % "scala-compiler" % _),
    libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.4" % "test",
    libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.12.2" % "test",
    publishArtifact in Compile := false,
    unmanagedSourceDirectories in Test <<= (scalaSource in Test) { (root: File) =>
      // TODO: I haven't yet ported negative tests to SBT, so for now I'm excluding them
      val (anns :: Nil, others) = root.listFiles.toList.partition(_.getName == "annotations")
      val (negAnns, otherAnns) = anns.listFiles.toList.partition(_.getName == "neg")
      System.setProperty("sbt.paths.tests.scaladoc", anns.listFiles.toList.filter(_.getName == "scaladoc").head.getAbsolutePath)
      otherAnns ++ others
    },
    fullClasspath in Test := {
      val testcp = (fullClasspath in Test).value.files.map(_.getAbsolutePath).mkString(java.io.File.pathSeparatorChar.toString)
      sys.props("sbt.paths.tests.classpath") = testcp
      (fullClasspath in Test).value
    },
    dependencyClasspath in Compile := {
      (dependencyClasspath in Compile).value.
        map(f => {println(f.metadata.get(moduleID.key)); f}).
        filter(_.metadata.get(moduleID.key).get.toString().contains("paradise")).
      foreach(f => {
        System.setProperty("sbt.paths.plugin.jar", f.data.getAbsolutePath) // これをセットしておかないといくつかのテストケースで落ちる
        println(f.data)
      })
      (dependencyClasspath in Compile).value
    },
    scalacOptions ++= Seq()
    // scalacOptions ++= Seq("-Xprint:typer")
    // scalacOptions ++= Seq("-Xlog-implicits")
  )
}

import com.softwaremill.UpdateVersionInDocs

val scala211 = "2.11.12"
val scala212 = "2.12.13"
val scala213 = "2.13.5"
val scala3 = "3.0.0-RC3"

val buildSettings = commonSmlBuildSettings ++ ossPublishSettings ++ Seq(
  organization := "com.softwaremill.quicklens",
  updateDocs := UpdateVersionInDocs(sLog.value, organization.value, version.value, List(file("README.md"))),
  scalacOptions := Seq("-deprecation", "-feature", "-unchecked") // useful for debugging macros: "-Ycheck:all"
)

lazy val root =
  project
    .in(file("."))
    .settings(buildSettings)
    .settings(publishArtifact := false)
    .aggregate(quicklens.projectRefs: _*)

val versionSpecificScalaSources = {
  Compile / unmanagedSourceDirectories := {
    val current = (Compile / unmanagedSourceDirectories).value
    val sv = (Compile / scalaVersion).value
    val baseDirectory = (Compile / scalaSource).value
    val suffixes = CrossVersion.partialVersion(sv) match {
      case Some((2, 13)) => List("2", "2.13+")
      case Some((2, _))  => List("2", "2.13-")
      case Some((3, _))  => List("3")
      case _             => Nil
    }
    val versionSpecificSources = suffixes.map(s => new File(baseDirectory.getAbsolutePath + "-" + s))
    versionSpecificSources ++ current
  }
}

def compilerLibrary(scalaVersion: String) = {
  if (scalaVersion == scala3) {
    Seq.empty
  } else {
    Seq("org.scala-lang" % "scala-compiler" % scalaVersion % Test)
  }
}

def reflectLibrary(scalaVersion: String) = {
  if (scalaVersion == scala3) {
    Seq.empty
  } else {
    Seq("org.scala-lang" % "scala-reflect" % scalaVersion % Provided)
  }
}

lazy val quicklens = (projectMatrix in file("quicklens"))
  .settings(buildSettings)
  .settings(
    name := "quicklens",
    libraryDependencies ++= reflectLibrary(scalaVersion.value),
    Test / publishArtifact := false,
    libraryDependencies ++= compilerLibrary(scalaVersion.value),
    versionSpecificScalaSources,
    libraryDependencies ++= Seq("flatspec", "shouldmatchers").map(m =>
      "org.scalatest" %%% s"scalatest-$m" % "3.2.8" % Test
    )
  )
  .jvmPlatform(
    scalaVersions = List(scala211, scala212, scala213, scala3)
  )
  .jsPlatform(
    scalaVersions = List(scala212, scala213) // TODO: add scala3
  )
  .nativePlatform(
    scalaVersions = List(scala211, scala212, scala213),
    settings = Seq(
      nativeLinkStubs := true
    )
  )

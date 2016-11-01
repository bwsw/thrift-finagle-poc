lazy val commons = Seq(
  version := "1.0",
  scalaVersion := "2.11.8",

  scroogeBuildOptions := Seq("-finagle", "-verbose")
)

lazy val scroogeServer = (project in file("."))
  .settings(commons: _*)
  .settings(
    libraryDependencies ++= Seq(
      "org.apache.thrift" % "libthrift" % "0.9.3",
      "com.twitter" %% "scrooge-core" % "4.11.0",
      "com.twitter" %% "finagle-thrift" % "6.34.0",
      "com.typesafe" % "config" % "1.3.1"
    )
)

lazy val scroogeClient = project
  .dependsOn(scroogeServer)
  .settings(commons: _*)

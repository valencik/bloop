scalaVersion := "2.12.4"

// All the plugins added in here are global to all the builds!
addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.1")
addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "0.9.3")
addSbtPlugin("ch.epfl.scala" % "sbt-release-early" % "2.1.0")
addSbtPlugin("com.lucidchart" % "sbt-scalafmt-coursier" % "1.15")

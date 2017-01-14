scalacOptions := Seq("-feature", "-deprecation")

scalaVersion := "2.11.8"

scalafmtConfig in ThisBuild := Some(file(".scalafmt"))

fork in run := true

libraryDependencies += "default" %% "scala-icalendar" % "0.1-SNAPSHOT"
libraryDependencies += "org.slf4j" % "slf4j-simple" % "1.7.22"
libraryDependencies += "net.bzzt" %% "ectrace" % "0.1-SNAPSHOT"
libraryDependencies += "net.ruippeixotog" %% "scala-scraper" % "1.1.0-SNAPSHOT"

libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.0" % "test"

libraryDependencies += "com.amazonaws" % "aws-lambda-java-core" % "1.0.0"
libraryDependencies += "net.databinder.dispatch" %% "dispatch-core" % "0.11.2"

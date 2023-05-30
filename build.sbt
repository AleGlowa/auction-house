name := "auction-house"

version := "1.0"

scalaVersion := "2.13.8"

lazy val akkaVersion = "2.6.19"
lazy val akkaHttpVersion = "10.2.9"
lazy val slickVersion = "3.3.3"

scalacOptions ++= Seq(
  "-unchecked",
  "-deprecation",
  "-feature",
  "-Wunused",
  "-language:implicitConversions",
  "-Ymacro-annotations"
)

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-persistence-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-sharding-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence-query" % akkaVersion,
  "com.typesafe.akka" %% "akka-serialization-jackson" % akkaVersion,
  "com.lightbend.akka" %% "akka-persistence-jdbc" % "5.0.4",
  "org.postgresql" % "postgresql" % "42.4.2",
  "io.argonaut" %% "argonaut" % "6.3.8",
  "com.pauldijou" %% "jwt-argonaut" % "5.0.0",
  "de.heikoseeberger" %% "akka-http-argonaut" % "1.40.0-RC2",
  "ch.qos.logback" % "logback-classic" % "1.2.11",
  "io.estatico" %% "newtype" % "0.4.4",
  "com.softwaremill.quicklens" % "quicklens_2.13" % "1.8.8",
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
  "org.scalatest" %% "scalatest" % "3.2.12" % Test
)

addCommandAlias("fmt", "scalafmtAll;scalafmtSbt")
addCommandAlias("check", "scalafmtCheckAll;scalafmtSbtCheck")

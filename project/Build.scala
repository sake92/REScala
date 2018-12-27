import sbt._
import Keys._
import org.scalajs.sbtplugin.ScalaJSPlugin
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import sbtcrossproject.CrossPlugin
import sbtcrossproject.CrossPlugin.autoImport._
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._


object Settings {

  val scala_212 = "2.12.8"

  val strictScalacOptions = Seq(
      "-deprecation",
      "-encoding", "UTF-8",
      "-unchecked",
      "-feature",
      "-target:jvm-1.8",
      "-Xlint",
      "-Xfuture",
      //"-Xlog-implicits" ,
      //"-Yno-predef" ,
      //"-Yno-imports" ,
      "-Xfatal-warnings",
      //"-Yinline-warnings" ,
      "-Yno-adapted-args",
      //"-Ywarn-dead-code" ,
      "-Ywarn-nullary-override",
      "-Ywarn-nullary-unit",
      "-Ywarn-numeric-widen",
      //"-Ywarn-value-discard" ,
    )
}

object Resolvers {
  val rmgk = Resolver.bintrayRepo("rmgk", "maven")
  val stg = Resolver.bintrayRepo("stg-tud", "maven")
  val all = Seq(rmgk, stg)
}

object Dependencies {
  val akkaHttp = libraryDependencies ++= (Seq("akka-http-core", "akka-http").map(n => "com.typesafe.akka" %% n % "10.1.5"))
  val akkaStream = libraryDependencies += ("com.typesafe.akka" %% "akka-stream" % "2.5.19")
  val betterFiles = libraryDependencies += ("com.github.pathikrit" %% "better-files" % "3.7.0")
  val circe = libraryDependencies ++= Seq("core", "generic", "generic-extras", "parser").map(n => "io.circe" %%% s"circe-$n" % "0.10.1")
  val decline = libraryDependencies += ("com.monovore" %% "decline" % "0.5.0")
  val jsoup = libraryDependencies += ("org.jsoup" % "jsoup" % "1.11.3")
  val lociCommunication = libraryDependencies ++= (Seq("communication", "communicator-ws-akka", "serializer-circe").map(n => "de.tuda.stg" %%% s"scala-loci-$n" % "0.2.0"))
  val purecss = libraryDependencies += ("org.webjars.npm" % "purecss" % "1.0.0")
  val rmgkLogging = libraryDependencies += ("de.rmgk" %%% "logging" % "0.2.1")
  val scalacheck = libraryDependencies += ("org.scalacheck" %% "scalacheck" % "1.14.0" % "test")
  val scalactic = libraryDependencies += ("org.scalactic" %% "scalactic" % "3.0.5")
  val scalajsdom = libraryDependencies += ("org.scala-js" %%% "scalajs-dom" % "0.9.6")
  val scalatags = libraryDependencies += ("com.lihaoyi" %%% "scalatags" % "0.6.7")
  val scalatest = libraryDependencies += ("org.scalatest" %% "scalatest" % "3.0.5" % "test")
}

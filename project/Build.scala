import sbt._
import sbt.Keys._
import com.typesafe.sbt.SbtScalariform._
import scalariform.formatter.preferences._
import scoverage.ScoverageKeys._
import com.typesafe.sbt.SbtGhPages.ghpages
import com.typesafe.sbt.SbtSite.site
import sbtunidoc.Plugin.UnidocKeys._
import sbtunidoc.Plugin._
import com.typesafe.sbt.SbtGit.{ GitKeys => git }

import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._

object Enumeratum extends Build {

  lazy val theVersion = "1.4.4-SNAPSHOT"
  lazy val theScalaVersion = "2.11.8"
  lazy val scalaVersions = Seq("2.10.6", "2.11.8")
  def thePlayVersion(scalaVersion: String) = CrossVersion.partialVersion(scalaVersion) match {
    case Some((2, scalaMajor)) if scalaMajor >= 11 => "2.5.3"
    case Some((2, scalaMajor)) if scalaMajor == 10 => "2.4.6"
    case _ => throw new IllegalArgumentException(s"Unsupported Scala version $scalaVersion")
  }
  lazy val scalaTestVersion = "3.0.0-M16-SNAP3"
  lazy val reactiveMongoVersion = "0.11.11"

  lazy val root = Project(id = "enumeratum-root", base = file("."), settings = commonWithPublishSettings)
    .settings(
      name := "enumeratum-root",
      crossScalaVersions := scalaVersions,
      crossVersion := CrossVersion.binary
    )
    .settings(unidocSettings: _*)
    .settings(site.settings ++ ghpages.settings: _*)
    .settings(
      site.addMappingsToSiteDir(
        mappings in (ScalaUnidoc, packageDoc), "latest/api"
      ),
      git.gitRemoteRepo := "git@github.com:lloydmeta/enumeratum.git"
    )
    .settings(
      scalacOptions in (ScalaUnidoc, unidoc) += "-Ymacro-no-expand",
      // Do not publish the root project (it just serves as an aggregate)
      publishArtifact := false,
      publishLocal := {}
    )
    .aggregate(macrosJs, macrosJvm, coreJs, coreJvm, coreJVMTests, enumeratumPlay, enumeratumPlayJson, enumeratumUPickleJs, enumeratumUPickleJvm, enumeratumCirceJs, enumeratumCirceJvm, enumeratumReactiveMongoBson)

  lazy val core = crossProject.crossType(CrossType.Pure).in(file("enumeratum-core"))
    .settings(
      name := "enumeratum"
    )
    .settings(withCompatUnmanagedSources(jsJvmCrossProject = true, include_210Dir = false, includeTestSrcs = true):_*)
    .settings(testSettings:_*)
    .settings(commonWithPublishSettings:_*)
    .dependsOn(macros)

  lazy val coreJs = core.js
  lazy val coreJvm = core.jvm

  lazy val coreJVMTests = Project(id = "coreJVMTests", base = file("enumeratum-core-jvm-tests"), settings = commonWithPublishSettings)
    .settings(
      name := "coreJVMTests",
      libraryDependencies ++= Seq(
        "org.scala-lang" % "scala-compiler" % scalaVersion.value % Test
      ),
      publishArtifact := false
    )
    .settings(testSettings:_*)
    .dependsOn(coreJvm)

  lazy val macros = crossProject.crossType(CrossType.Pure).in(file("macros"))
    .settings(commonWithPublishSettings:_*)
    .settings(
      name := "enumeratum-macros",
      libraryDependencies ++= Seq(
        "org.scala-lang" % "scala-reflect" % scalaVersion.value
      )
    )
    .settings(withCompatUnmanagedSources(jsJvmCrossProject = true, include_210Dir = true, includeTestSrcs = false):_*)
    .settings(testSettings:_*)
  lazy val macrosJs = macros.js
  lazy val macrosJvm = macros.jvm

  lazy val enumeratumReactiveMongoBson = Project(id = "enumeratum-reactivemongo-bson", base = file("enumeratum-reactivemongo-bson"), settings = commonWithPublishSettings)
    .settings(
      libraryDependencies ++= Seq(
        "org.reactivemongo" %% "reactivemongo" % reactiveMongoVersion
      )
    )
    .settings(withCompatUnmanagedSources(jsJvmCrossProject = false, include_210Dir = false, includeTestSrcs = true):_*)
    .settings(testSettings:_*)
    .dependsOn(coreJvm % "test->test;compile->compile")

  lazy val enumeratumPlayJson = Project(id = "enumeratum-play-json", base = file("enumeratum-play-json"), settings = commonWithPublishSettings)
    .settings(
      libraryDependencies ++= Seq(
        "com.typesafe.play" %% "play-json" % thePlayVersion(scalaVersion.value)
      )
    )
    .settings(withCompatUnmanagedSources(jsJvmCrossProject = false, include_210Dir = false, includeTestSrcs = true):_*)
    .settings(testSettings:_*)
    .dependsOn(coreJvm % "test->test;compile->compile")

  lazy val enumeratumPlay = Project(id = "enumeratum-play", base = file("enumeratum-play"), settings = commonWithPublishSettings)
    .settings(
      libraryDependencies ++= Seq(
        "com.typesafe.play" %% "play" % thePlayVersion(scalaVersion.value)
      )
    )
    .settings(withCompatUnmanagedSources(jsJvmCrossProject = false, include_210Dir = false, includeTestSrcs = true):_*)
    .settings(testSettings:_*)
    .dependsOn(coreJvm, enumeratumPlayJson % "test->test;compile->compile")

  lazy val enumeratumUPickle = crossProject.crossType(CrossType.Pure).in(file("enumeratum-upickle"))
    .settings(commonWithPublishSettings:_*)
    .settings(
      name := "enumeratum-upickle",
      libraryDependencies ++= {
        import org.scalajs.sbtplugin._
        val cross = {
          if (ScalaJSPlugin.autoImport.jsDependencies.?.value.isDefined)
            ScalaJSCrossVersion.binary
          else
            CrossVersion.binary
        }
        Seq(impl.ScalaJSGroupID.withCross("com.lihaoyi", "upickle", cross) % "0.3.9")
      } ++ {
        val additionalMacroDeps = CrossVersion.partialVersion(scalaVersion.value) match {
          // if scala 2.11+ is used, quasiquotes are merged into scala-reflect
          case Some((2, scalaMajor)) if scalaMajor >= 11 =>
            Nil
          // in Scala 2.10, quasiquotes are provided by macro paradise
          case Some((2, 10)) =>
            Seq(
              "org.scalamacros" %% "quasiquotes" % "2.0.1" cross CrossVersion.binary )
        }
        additionalMacroDeps
      }
    )
    .settings(withCompatUnmanagedSources(jsJvmCrossProject = true, include_210Dir = false, includeTestSrcs = true):_*)
    .settings(testSettings:_*)
    .dependsOn(core % "test->test;compile->compile")
  lazy val enumeratumUPickleJs = enumeratumUPickle.js
  lazy val enumeratumUPickleJvm = enumeratumUPickle.jvm

  lazy val enumeratumCirce = crossProject.crossType(CrossType.Pure).in(file("enumeratum-circe"))
    .settings(commonWithPublishSettings:_*)
    .settings(
      name := "enumeratum-circe",
      libraryDependencies ++= {
        import org.scalajs.sbtplugin._
        val cross = {
          if (ScalaJSPlugin.autoImport.jsDependencies.?.value.isDefined)
            ScalaJSCrossVersion.binary
          else
            CrossVersion.binary
        }
        Seq(impl.ScalaJSGroupID.withCross("io.circe", "circe-core", cross) % "0.4.1")
      }
    )
    .settings(withCompatUnmanagedSources(jsJvmCrossProject = true, include_210Dir = false, includeTestSrcs = true):_*)
    .settings(testSettings:_*)
    .dependsOn(core % "test->test;compile->compile")
  lazy val enumeratumCirceJs = enumeratumCirce.js
  lazy val enumeratumCirceJvm = enumeratumCirce.jvm


  lazy val commonSettings = Seq(
    organization := "com.beachape",
    version := theVersion,
    scalaVersion := theScalaVersion
  ) ++
    scalariformSettings ++
    scoverageSettings ++
    formatterPrefs ++
    compilerSettings ++
    resolverSettings ++
    ideSettings

  lazy val formatterPrefs = Seq(
    ScalariformKeys.preferences := ScalariformKeys.preferences.value
      .setPreference(AlignParameters, true)
      .setPreference(DoubleIndentClassDeclaration, true)
  )

  lazy val commonWithPublishSettings =
    commonSettings ++
      publishSettings

  lazy val resolverSettings = Seq(
    resolvers += "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases/",
    resolvers += "Sonatype snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
  )

  lazy val ideSettings = Seq(
    // Faster "sbt gen-idea"
    transitiveClassifiers in Global := Seq(Artifact.SourceClassifier)
  )

  lazy val compilerSettings = Seq(
    // the name-hashing algorithm for the incremental compiler.
    incOptions := incOptions.value.withNameHashing(nameHashing = true),
    scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-Xlint", "-Xlog-free-terms")
  )

  lazy val scoverageSettings = Seq(
    coverageExcludedPackages := """enumeratum\.EnumMacros;enumeratum\.ContextUtils;enumeratum\.ValueEnumMacros""",
    coverageHighlighting := true
  )

  // Settings for publishing to Maven Central
  lazy val publishSettings = Seq(
    pomExtra :=
      <url>https://github.com/lloydmeta/enumeratum</url>
        <licenses>
          <license>
            <name>MIT</name>
            <url>http://opensource.org/licenses/MIT</url>
            <distribution>repo</distribution>
          </license>
        </licenses>
        <scm>
          <url>git@github.com:lloydmeta/enumeratum.git</url>
          <connection>scm:git:git@github.com:lloydmeta/enumeratum.git</connection>
        </scm>
        <developers>
          <developer>
            <id>lloydmeta</id>
            <name>Lloyd Chan</name>
            <url>https://beachape.com</url>
          </developer>
        </developers>,
    publishTo <<= version { v =>
      val nexus = "https://oss.sonatype.org/"
      if (v.trim.endsWith("SNAPSHOT"))
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases"  at nexus + "service/local/staging/deploy/maven2")
    },
    publishMavenStyle := true,
    publishArtifact in Test := false,
    pomIncludeRepository := { _ => false }
  )

  val testSettings = {
    Seq(
      libraryDependencies += {
        import org.scalajs.sbtplugin._
        val crossVersion = if (ScalaJSPlugin.autoImport.jsDependencies.?.value.isDefined)
          ScalaJSCrossVersion.binary
        else
          CrossVersion.binary
        impl.ScalaJSGroupID.withCross("org.scalatest", "scalatest", crossVersion) % scalaTestVersion % Test
      },
      scalaJSStage in Test := FastOptStage
    )
  }

  /**
    * Helper function to add unmanaged source compat directories for different scala versions
    */
  private def withCompatUnmanagedSources(jsJvmCrossProject: Boolean, include_210Dir: Boolean, includeTestSrcs: Boolean): Seq[Setting[_]] = {
    def compatDirs(projectbase: File, scalaVersion: String, isMain: Boolean) = {
      val base = if (jsJvmCrossProject ) projectbase / ".." else projectbase
      CrossVersion.partialVersion(scalaVersion) match {
        case Some((2, scalaMajor)) if scalaMajor >= 11 => Seq(base / "compat" / "src" / (if (isMain) "main" else "test") / "scala-2.11").map(_.getCanonicalFile)
        case Some((2, scalaMajor)) if scalaMajor == 10 && include_210Dir => Seq(base / "compat" / "src" / (if (isMain) "main" else "test") / "scala-2.10").map(_.getCanonicalFile)
        case _ => Nil
      }
    }
    val unmanagedMainDirsSetting = Seq(
      unmanagedSourceDirectories in Compile ++= {
        compatDirs(projectbase = baseDirectory.value, scalaVersion = scalaVersion.value, isMain = true)
      }
    )
    if (includeTestSrcs) {
      unmanagedMainDirsSetting ++ {
        unmanagedSourceDirectories in Test ++= {
          compatDirs(projectbase = baseDirectory.value, scalaVersion = scalaVersion.value, isMain = false)
        }
      }
    } else {
      unmanagedMainDirsSetting
    }
  }

}

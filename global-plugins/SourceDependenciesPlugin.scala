package ch.epfl.scala.sbt.sourcedeps

import java.io.File

import sbt.{AutoPlugin, ClasspathDependency, Compile, Def, Defaults, Keys, PluginTrigger, Plugins, Project, ProjectDefinition, ProjectOrigin, ProjectRef, RootProject, Test, ThisBuild, file, uri}
import sbt.io.syntax.fileToRichFile
import sbt.librarymanagement.{UpdateReport, UpdateStats}
import sbt.librarymanagement.syntax.stringToOrganization
import sbt.plugins.{CorePlugin, JvmPlugin}
import sbtdynver.DynVerPlugin.{autoImport => DynVerKeys}
import sbtdynver.GitDescribeOutput

object SourceDependenciesPlugin extends AutoPlugin {
  override def trigger: PluginTrigger = allRequirements
  override def requires: Plugins = JvmPlugin
  val autoImport = PluginKeys

  override def globalSettings: Seq[Def.Setting[_]] =
    PluginImplementation.globalSettings
  override def buildSettings: Seq[Def.Setting[_]] =
    PluginImplementation.buildSettings
  override def projectSettings: Seq[Def.Setting[_]] =
    PluginImplementation.projectSettings

  // Thes settings are necesssary because `GlobalPlugin` needs them.
  val settingsToMakeSbtNotFail: Seq[Def.Setting[_]] = List(
    Keys.projectDescriptors := Map.empty,
    Keys.projectDependencies := Nil,
    Keys.resolvers := Nil,
    Keys.globalPluginUpdate := {
      val cachedDescriptor = Keys.target.value / ".cached-descriptor"
      val emptyStats = UpdateStats(0, 0, 0, true)
      UpdateReport.apply(cachedDescriptor, Vector(), emptyStats, Map.empty)
    },
    Keys.internalDependencyClasspath in sbt.Compile := Nil,
    Keys.internalDependencyClasspath in sbt.Runtime := Nil
  )

  val baseDirectory = Option(System.getProperty("sbt.global.plugins"))
    .map(f => file(f).getParentFile)
    .getOrElse(sys.error("Missing `sbt.global.plugins` in .sbtopts!"))

  /**
    * An override project only adds overrides to the original project and makes sure
    * that sbt does not fully resolve all the settings for it (disabling core plugin
    * makes the memory consumption of every project negligible!).
    *
    * @param proj The project which we want to override.
    * @return The new derived project with the overrides.
    */
  def createOverride(proj: Project): Project = {
    val overrideId = s"${proj.id}-override"
    Project(overrideId, proj.base./("target")./(overrideId))
      .dependsOn(ClasspathDependency(proj, None))
      .settings(Defaults.coreDefaultSettings)
      .settings(settingsToMakeSbtNotFail)
      .disablePlugins(CorePlugin)
  }


  override def derivedProjects(proj: ProjectDefinition[_]): Seq[Project] = {
    proj match {
      case proj: Project if proj.projectOrigin != ProjectOrigin.DerivedProject =>
        val overrides = PluginImplementation.PluginDefaults.genProjectSettings(proj)
        List(createOverride(proj).settings(overrides))
      case _ => super.derivedProjects(proj)
    }
  }
}

object PluginKeys {}

object PluginImplementation {
  val globalSettings: Seq[Def.Setting[_]] = Nil
  val buildSettings: Seq[Def.Setting[_]] = Nil
  val projectSettings: Seq[Def.Setting[_]] = Nil

  object PluginDefaults {
    private val isCiDisabled = sys.env.get("CI").isEmpty
    def createScalaCenterProject(name: String, f: File): RootProject = {
      if (isCiDisabled) RootProject(f)
      else {
        val headSha = new com.typesafe.sbt.git.DefaultReadableGit(f).withGit(_.headCommitSha)
        headSha match {
          case Some(commit) => RootProject(uri(s"git://github.com/scalacenter/${name}.git#$commit"))
          case None => sys.error(s"The 'HEAD' sha of '${f}' could not be retrieved.")
        }
      }
    }

    def inProject(ref: sbt.Reference)(ss: Seq[Def.Setting[_]]): Seq[Def.Setting[_]] =
      sbt.inScope(sbt.ThisScope.in(project = ref))(ss)

    /**
     * This setting figures out whether the version is a snapshot or not and configures
     * the source and doc artifacts that are published by the build.
     *
     * Snapshot is a term with no clear definition. In this code, a snapshot is a revision
     * that is dirty, e.g. has time metadata in its representation. In those cases, the
     * build will not publish doc and source artifacts by any of the publishing actions.
     */
    def publishDocAndSourceArtifact(info: Option[GitDescribeOutput], version: String): Boolean = {
      val isStable = info.map(_.dirtySuffix.value.isEmpty)
      !isStable.map(stable => !stable || version.endsWith("-SNAPSHOT")).getOrElse(false)
    }

    def genProjectSettings(ref: Project): Seq[Def.Setting[_]] =
      inProject(ref)(
        List(
          Keys.organization := "ch.epfl.scala",
          Keys.homepage := {
            val previousHomepage = Keys.homepage.in(ref).value
            if (previousHomepage.nonEmpty) previousHomepage
            else (Keys.homepage in ThisBuild).value
          },
          Keys.developers := {
            val previousDevelopers = Keys.developers.in(ref).value
            if (previousDevelopers.nonEmpty) previousDevelopers
            else (Keys.developers.in(ref).in(ThisBuild)).value
          },
          Keys.licenses := {
            val previousLicenses = Keys.licenses.in(ref).value
            if (previousLicenses.nonEmpty) previousLicenses
            else (Keys.licenses in ThisBuild).value
          },
          Keys.publishArtifact in Test := false,
          Keys.publishArtifact in (Compile, Keys.packageDoc) := {
            val output = DynVerKeys.dynverGitDescribeOutput.in(ref).in(ThisBuild).value
            val version = Keys.version.in(ref).value
            publishDocAndSourceArtifact(output, version)
          },
          Keys.publishArtifact in (Compile, Keys.packageSrc) := {
            val output = DynVerKeys.dynverGitDescribeOutput.in(ref).in(ThisBuild).value
            val version = Keys.version.in(ref).value
            publishDocAndSourceArtifact(output, version)
          }
        ))
  }
  //) ++ sharedBuildPublishSettings ++ sharedProjectPublishSettings)
}

object SourceDependencies {
  import sbt.{BuildRef, ProjectRef}
  import PluginImplementation.PluginDefaults.createScalaCenterProject
  // Use absolute paths so that references work even if `ThisBuild` changes
  final val AbsolutePath = file(".").getCanonicalFile.getAbsolutePath

  object Zinc {
    final val ZincRoot = createScalaCenterProject("zinc", file(s"$AbsolutePath/zinc"))
    final val ZincBuild = BuildRef(ZincRoot.build)
    final val ZincProject = ProjectRef(ZincRoot.build, "zinc")
    final val ZincRootProject = ProjectRef(ZincRoot.build, "zincRoot")
    final val ZincBridge = ProjectRef(ZincRoot.build, "compilerBridge")

    val globalSettings: Seq[Def.Setting[_]] = Nil
    val buildSettings: Seq[Def.Setting[_]] = Nil
    val projectSettings: Seq[Def.Setting[_]] = Nil
  }

  object Nailgun {
    final val NailgunRoot = createScalaCenterProject("nailgun", file(s"$AbsolutePath/nailgun"))
    final val NailgunBuild = BuildRef(NailgunRoot.build)
    final val NailgunProject = ProjectRef(NailgunRoot.build, "nailgun")
    final val NailgunServer = ProjectRef(NailgunRoot.build, "nailgun-server")
    final val NailgunExamples = ProjectRef(NailgunRoot.build, "nailgun-examples")

    val globalSettings: Seq[Def.Setting[_]] = Nil
    val buildSettings: Seq[Def.Setting[_]] = Nil
    val projectSettings: Seq[Def.Setting[_]] = Nil
  }

  object BenchmarkBridge {
    final val BenchmarkRoot =
      createScalaCenterProject("compiler-benchmark", file(s"$AbsolutePath/benchmark-bridge"))
    final val BenchmarkBridgeBuild = BuildRef(BenchmarkRoot.build)
    final val BenchmarkBridgeCompilation = ProjectRef(BenchmarkRoot.build, "compilation")

    val globalSettings: Seq[Def.Setting[_]] = Nil
    val buildSettings: Seq[Def.Setting[_]] = Nil
    val projectSettings: Seq[Def.Setting[_]] = Nil
  }

  object Bsp {
    final val BspRoot = createScalaCenterProject("bsp", file(s"$AbsolutePath/bsp"))
    final val BspBuild = BuildRef(BspRoot.build)
    final val BspProject = ProjectRef(BspRoot.build, "bsp")

    val globalSettings: Seq[Def.Setting[_]] = Nil
    val buildSettings: Seq[Def.Setting[_]] = Nil
    val projectSettings: Seq[Def.Setting[_]] = Nil
  }
}

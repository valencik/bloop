package ch.epfl.scala.sbt.gradle

import sbt.{AutoPlugin, Compile, Def, Keys, PluginTrigger, Plugins}

object GradlePluginIntegration extends AutoPlugin {
  override def trigger: PluginTrigger = allRequirements
  override def requires: Plugins = sbt.plugins.JvmPlugin
  val autoImport = GradlePluginKeys

  override def globalSettings: Seq[Def.Setting[_]] =
    GradlePluginImplementation.globalSettings
  override def buildSettings: Seq[Def.Setting[_]] =
    GradlePluginImplementation.buildSettings
  override def projectSettings: Seq[Def.Setting[_]] =
    GradlePluginImplementation.projectSettings
}

object GradlePluginKeys {
  import sbt.{settingKey, taskKey}
  val gradlePlugin = settingKey[Boolean]("If true, makes this project a gradle plugin")
  val gradlePluginNamespace =
    settingKey[Option[String]]("The gradle plugin namespace for the plugin properties file.")
  val gradlePluginClass =
    taskKey[Option[String]]("Defines the FQN for the Gradle plugin in the project.")
}

object GradlePluginImplementation {
  val globalSettings: Seq[Def.Setting[_]] = Nil
  val buildSettings: Seq[Def.Setting[_]] = Nil
  val projectSettings: Seq[Def.Setting[_]] = List(
    GradlePluginKeys.gradlePlugin := false,
    GradlePluginKeys.gradlePluginClass := None,
    GradlePluginKeys.gradlePluginNamespace := None,
    Keys.resourceGenerators in Compile ++= {
      if (!GradlePluginKeys.gradlePlugin.value) Nil
      else List(MavenPluginDefaults.resourceGenerators.taskValue)
    }
  )

  object MavenPluginDefaults {

    import sbt.{Task, File, IO}
    import sbt.io.syntax.fileToRichFile
    def writePluginPropertiesFile(f: File, namespace: String, pluginClass: String): Unit = {
      val contents = s"implementation-class=$pluginClass"
      IO.write(f, contents)
    }

    val resourceGenerators: Def.Initialize[Task[Seq[File]]] = Def.task {
      if (!GradlePluginKeys.gradlePlugin.value) Nil
      else {
        val namespace = GradlePluginKeys.gradlePluginNamespace.value
          .getOrElse(sys.error("There is no gradle plugin namespace defined"))
        val pluginsDir = Keys.resourceManaged.in(Compile).value./("META-INF/gradle-plugins")
        val target = pluginsDir / s"$namespace.properties"
        val pluginClass = GradlePluginKeys.gradlePluginClass.value
          .getOrElse(sys.error("There is no gradle plugin defined!"))
        writePluginPropertiesFile(target, namespace, pluginClass)
        Seq(target)
      }
    }
  }
}

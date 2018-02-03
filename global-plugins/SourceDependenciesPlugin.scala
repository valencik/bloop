package me.vican.jorge.sbt.sourcedeps

import sbt.{AutoPlugin, Command, Def, Keys, PluginTrigger, Plugins, Task, ThisBuild}
import sbt.io.syntax.fileToRichFile
import sbt.librarymanagement.syntax.stringToOrganization
import sbt.plugins.JvmPlugin

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
}

object PluginKeys {

}

object PluginImplementation {
  val globalSettings: Seq[Def.Setting[_]] = Nil
  val buildSettings: Seq[Def.Setting[_]] = Nil
  val projectSettings: Seq[Def.Setting[_]] = Nil
}

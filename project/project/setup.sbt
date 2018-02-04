import java.io.FileInputStream
import java.nio.file.{Files, Paths}

import scala.Console
val BloopPrefix = s"[${Console.BOLD}bloop${Console.RESET}]"
val SbtGlobalPlugins = "-Dsbt.global.plugins"
onLoad in Global := {
  // We need to throw a virtual machine error in order to sbt not to show the `load-failed` command
  object SetupSucceeded
      extends VirtualMachineError(s"[${Console.GREEN}success${Console.RESET}] Bloop completed its setup.")
      with scala.util.control.NoStackTrace {
    private var counter = 0
    override def toString: String = {
      counter += 1
      if (counter == 1) this.getMessage()
      else s"${Console.RED}Please, run sbt again! :)${Console.RESET}"
    }
  }

  val logger = Keys.sLog.value
  val setUpAlternativeGlobal = { (state: State) =>
    val bloopBaseDir = (baseDirectory in ThisBuild).value.getParentFile.getParentFile
    val sbtOptsFile = bloopBaseDir / ".sbtopts"
    val properties = new java.util.Properties
    properties.load(new FileInputStream(sbtOptsFile))
    val globalBase = Option(properties.getProperty(SbtGlobalPlugins))
    if (globalBase.isDefined) state
    else {
      logger.info(s"$BloopPrefix This is the first time you've cloned bloop. Let me set myself up.")

      // Tell sbt to use the local global base to load all the plugins
      val globalPlugins = bloopBaseDir / "global-plugins"
      val nl = System.lineSeparator
      val contents0 = if (sbtOptsFile.exists()) s"${IO.read(sbtOptsFile)}$nl" else ""
      val newDef = s"${SbtGlobalPlugins}=${globalPlugins.getAbsolutePath}"
      val contents = s"$contents0$newDef"
      IO.write(sbtOptsFile, contents)

      // Now, let's set up the local global plugins base before the reload
      val currentGlobalBase = BuildPaths.getGlobalBase(state)
      val currentPluginsBase = BuildPaths.getGlobalPluginsDirectory(state, currentGlobalBase)
      logger.info(s"$BloopPrefix Symlinking sbt global in '${globalPlugins}'.")
      mirrorDirectory(currentPluginsBase, globalPlugins, Nil, logger)

      logger.info(s"$BloopPrefix I am going to exit forcefully now, sbt gives me no option.")
      throw SetupSucceeded
    }
  }

  val previous = (onLoad in Global).value
  setUpAlternativeGlobal.compose(previous)
}

// Create symbolic links for all directories in the old global except plugins.
def mirrorDirectory(dir: File, target: File, skipNames: List[String], logger: Logger): Unit = {
  IO.listFiles(dir).foreach { f =>
    val path = f.toPath
    val name = path.getFileName.toString
    if (skipNames.contains(name)) ()
    else {
      val newLink = Paths.get(s"${target.getAbsolutePath}/${name}")
      if (Files.exists(newLink)) sys.error(s"File '${f.getAbsolutePath}' already exists!")
      else {
        logger.info(s"$BloopPrefix => Creating a symbolic link in '$newLink'.")
        Files.createSymbolicLink(newLink, path)
      }
    }
  }
}

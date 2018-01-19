package bloop

import java.util.concurrent.ConcurrentHashMap

import bloop.io.{AbsolutePath, Paths}
import bloop.logging.Logger
import sbt.internal.inc.bloop.ZincInternals
import sbt.internal.inc.{AnalyzingCompiler, ZincUtil}
import sbt.librarymanagement.Resolver
import xsbti.ComponentProvider
import xsbti.compile.{ClasspathOptions, Compilers}

class CompilerCache(componentProvider: ComponentProvider,
                    retrieveDir: AbsolutePath,
                    logger: Logger,
                    userResolvers: List[Resolver]) {

  private type CompilerID = (ScalaInstance, ClasspathOptions)
  private val cache = new ConcurrentHashMap[CompilerID, Compilers]()

  def get(compilerID: CompilerID): Compilers =
    cache.computeIfAbsent(compilerID, newCompilers)

  private def newCompilers(compilerID: CompilerID): Compilers = {
    val (scalaInstance, classpathOptions) = compilerID
    val compiler = getScalaCompiler(scalaInstance, classpathOptions, componentProvider)
    ZincUtil.compilers(scalaInstance, classpathOptions, None, compiler)
  }

  def getScalaCompiler(scalaInstance: ScalaInstance,
                       classpathOptions: ClasspathOptions,
                       componentProvider: ComponentProvider): AnalyzingCompiler = {
    val bridgeSources = ZincInternals.getModuleForBridgeSources(scalaInstance)
    val bridgeId = ZincInternals.getBridgeComponentId(bridgeSources, scalaInstance)
    componentProvider.component(bridgeId) match {
      case Array(jar) => ZincUtil.scalaCompiler(scalaInstance, jar, classpathOptions)
      case _ =>
        ZincUtil.scalaCompiler(
          /* scalaInstance        = */ scalaInstance,
          /* classpathOptions     = */ classpathOptions,
          /* globalLock           = */ BloopComponentsLock,
          /* componentProvider    = */ componentProvider,
          /* secondaryCacheDir    = */ Some(Paths.getCacheDirectory("bridge-cache").toFile),
          /* dependencyResolution = */ DependencyResolution.getEngine(userResolvers),
          /* compilerBridgeSource = */ bridgeSources,
          /* scalaJarsTarget      = */ retrieveDir.toFile,
          /* log                  = */ logger
        )
    }
  }
}

package bloop.nailgun

import org.junit.Assert.{assertEquals, assertNotEquals}

import java.nio.file.{Files, Path, Paths}

import bloop.Server
import bloop.logging.{ProcessLogger, RecordingLogger}
import bloop.tasks.ProjectHelpers

/**
 * Base class for writing test for the nailgun integration.
 */
abstract class NailgunTest {

  private final val TEST_PORT = 8998

  /**
   * Starts a Nailgun server, creates a client and executes operations with that client.
   * The server is shut down at the end of `op`.
   *
   * @param log  The logger that will receive all produced output.
   * @param base The base directory in which the client will be.
   * @param op   A function that will receive the instantiated Client.
   * @return The result of executing `op` on the client.
   */
  def withServer[T](log: RecordingLogger, base: Path)(op: Client => T): T = {
    val serverThread =
      new Thread {
        override def run(): Unit = {
          val outStream = ProcessLogger.toOutputStream(log.info)
          val errStream = ProcessLogger.toOutputStream(log.error)
          Console.withOut(outStream) {
            Console.withErr(errStream) {
              Server.main(Array(TEST_PORT.toString))
            }
          }
        }
      }

    serverThread.start()

    Thread.sleep(500)
    val client = new Client(TEST_PORT, log, base)
    try op(client)
    finally client.success("exit")
  }

  /**
   * Starts a server and provides a client in `base`. A logger that will receive
   * all output will be created and passed to `op`.
   *
   * @param base The base directory where the client will be.
   * @param op   A function that accepts a logger and a client.
   * @return The result of executing `op` on the logger and client.
   */
  def withServerIn[T](base: Path)(op: (RecordingLogger, Client) => T): T = {
    val logger = new RecordingLogger
    withServer(logger, base)(op(logger, _))
  }

  /**
   * Starts a server and provides a client in the directory of project `name`.
   * A logger that will receive all output will be created and passed to `op`.
   *
   * @param base The base directory where the client will be.
   * @param op   A function that accepts a logger and a client.
   * @return The result of executing `op` on the logger and client.
   */
  def withServerInProject[T](name: String)(op: (RecordingLogger, Client) => T): T = {
    withServerIn(ProjectHelpers.getBloopConfigDir(name).getParent)(op)
  }

  /**
   * A client that interacts with a running Nailgun server.
   *
   * @param port The port on which the client should communicate with the server.
   * @param base The base directory from which the client is running.
   */
  case class Client(port: Int, log: RecordingLogger, base: Path) {

    private val clientPath = bloop.internal.build.BuildInfo.nailgunClientLocation.getAbsolutePath

    assert(Files.exists(base), s"Base directory doesn't exist: '$base'.")
    assert(Files.exists(Paths.get(clientPath)), s"Couldn't find Nailgun client at '$clientPath'.")

    private def processBuilder(cmd: Seq[String]): ProcessBuilder = {
      new ProcessBuilder((clientPath +: s"--nailgun-port=$port" +: cmd): _*)
        .directory(base.toFile)
    }

    /**
     * Executes a command `cmd` on the server, and return the exit code.
     *
     * @param cmd The command to execute
     * @return The exit code of the operation.
     */
    def issue(cmd: String*): Int = {
      val builder = processBuilder(cmd)
      val process = builder.start()
      val processLogger = new ProcessLogger(log, process)
      processLogger.start()
      process.waitFor()
    }

    /**
     * Executes a command `cmd` expecting a 0 exit code.
     *
     * @param cmd The command to run.
     */
    def success(cmd: String*): Unit = {
      val failMessage: String =
        s"""Success expected, but command failed. Logs were:
           |${log.getMessages.mkString("\n")}
           |""".stripMargin
      assertEquals(failMessage, 0, issue(cmd: _*).toLong)
    }

    /**
     * Executes a command `cmd`, expecting a non-zero exit code.
     *
     * @param cmd The command to run.
     */
    def fail(cmd: String*): Unit = {
      val failMessage: String =
        s"""Failure expected, but command succeeded. Logs were:
           |${log.getMessages.mkString("\n")}
           |""".stripMargin
      assertNotEquals(failMessage, 0, issue(cmd: _*).toLong)
    }

  }

}

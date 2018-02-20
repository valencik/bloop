package bloop.exec

import bloop.logging.{Logger, RecordingLogger}

import org.junit.Test
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.experimental.categories.Category

@Category(Array(classOf[bloop.FastTests]))
class MultiplexedStreamsSpec {

  private val EOL = System.lineSeparator

  @Test
  def systemOutAndErrAreHijacked() = {
    val logger = new RecordingLogger
    MultiplexedStreams.withLoggerAsStreams(logger) {
      System.out.println("stdout")
      System.err.println("stderr")
    }
    val messages = logger.getMessages
    val stdoutNeedle = ("info", "stdout")
    val stderrNeedle = ("error", "stderr")

    assertEquals(2, messages.length.toLong)
    assertTrue(s"${messages.mkString("\n")} didn't contain $stdoutNeedle",
               messages.contains(stdoutNeedle))
    assertTrue(s"${messages.mkString("\n")} didn't contain $stderrNeedle",
               messages.contains(stderrNeedle))
  }

  @Test
  def concurrentlyRunningProcessesHaveDifferentOutputs = {
    val monitor = new Object

    val l0 = new RecordingLogger
    val t0Name = "t0"
    val t0 = new TestThread(monitor, l0, t0Name)

    val l1 = new RecordingLogger
    val t1Name = "t1"
    val t1 = new TestThread(monitor, l1, t1Name)

    t0.start()
    t1.start()

    t0.join()
    t1.join()

    val messagesT0 = l0.getMessages
    val messagesT1 = l1.getMessages

    val expectedT0 = ("info", t0Name)
    val expectedT1 = ("info", t1Name)

    assertEquals(10, messagesT0.length.toLong)
    assertEquals(10, messagesT1.length.toLong)
    assertTrue(s"${messagesT0.mkString("\n")} were not all $expectedT0",
               messagesT0.forall(_ == expectedT0))
    assertTrue(s"${messagesT1.mkString("\n")} were not all $expectedT1",
               messagesT1.forall(_ == expectedT1))
  }

  private class TestThread(monitor: Object, logger: Logger, name: String) extends Thread {
    var i = 0

    override def run(): Unit = {
      MultiplexedStreams.withLoggerAsStreams(logger) {
        monitor.synchronized {
          while (i < 10) {
            i += 1
            System.out.println(name)
            monitor.notify()
            monitor.wait()
          }
          monitor.notify()
        }
      }
      ()
    }
  }
}

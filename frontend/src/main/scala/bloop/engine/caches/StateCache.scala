package bloop.engine.caches

import bloop.io.AbsolutePath
import java.util.concurrent.ConcurrentHashMap

import bloop.engine.State
final class StateCache(cache: ConcurrentHashMap[AbsolutePath, State]) {
  def updateBuild(state: State): State = cache.put(state.build.origin, state)
  def addIfMissing(from: AbsolutePath, computeBuild: AbsolutePath => State): State = {
    val state = cache.computeIfAbsent(from, p => computeBuild(p))
    if (!state.build.changed) state
    else {
      val updatedState = computeBuild(from)
      val _ = cache.put(from, updatedState)
      updatedState
    }
  }
  def allStates: Iterator[State] = {
    import scala.collection.JavaConverters._
    cache.asScala.valuesIterator
  }
}

object StateCache {
  def empty: StateCache = new StateCache(new ConcurrentHashMap())
}

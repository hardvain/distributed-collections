package scala.collection.distributed

import java.net.URI
import collection.immutable
import shared.DistHashMapBuilder


class DistHashMap[K, +V](uri: URI)
  extends DistMap[K, V]
  with GenericDistMapTemplate[K, V, DistHashMap]
  with DistMapLike[K, V, DistHashMap[K, V], immutable.Map[K, V]]
  with Serializable {
  self =>

  override def seq = remoteIterable.toMap

  def location = uri

  override def mapCompanion: GenericDistMapCompanion[DistHashMap] = DistHashMap

  override def empty: DistHashMap[K, V] = throw new UnsupportedOperationException("Not implemented yet!!")

  def -(k: K) = throw new UnsupportedOperationException("Not implemented yet!!")

  def +[U >: V](kv: (K, U)) = throw new UnsupportedOperationException("Not implemented yet!!")

  def get(k: K) = throw new UnsupportedOperationException("Not implemented yet!!")

  def view = throw new UnsupportedOperationException("Not implemented yet!!")
}

object DistHashMap extends DistMapFactory[DistHashMap] {
  def empty[K, V]: DistHashMap[K, V] = throw new UnsupportedOperationException("Not implmented yet!!")

  def newDistBuilder[K, V] = DistHashMapBuilder[K, V]()

  implicit def canBuildFrom[K, V]: CanDistBuildFrom[Coll, (K, V), DistHashMap[K, V]] = {
    new CanDistBuildFromMap[K, V]
  }

}
package scala.collection.distributed

import collection.GenSet
import collection.generic.GenericCompanion

/**
 * User: vjovanovic
 * Date: 4/26/11
 */

trait DistSet[T] extends GenSet[T]
with GenericDistTemplate[T, DistSet]
with DistIterable[T]
with DistSetLike[T, DistSet[T], Set[T]] {
  self =>
  override def empty: DistSet[T] = throw new UnsupportedOperationException("")

  //protected[this] override def newCombiner: Combiner[T, ParSet[T]] = ParSet.newCombiner[T]

  override def companion: GenericCompanion[DistSet] with GenericDistCompanion[DistSet] = DistSet

  override def seq = super.seq.toSet

  override def stringPrefix = "DistSet"
}

object DistSet extends DistSetFactory[DistSet] {
  implicit def canBuildFrom[T]: CanDistBuildFrom[Coll, T, DistSet[T]] = new GenericDistBuildFrom[T]

  def newRemoteBuilder[T]: RemoteBuilder[T, DistSet[T]] = new DistSetRemoteBuilder[T]
}
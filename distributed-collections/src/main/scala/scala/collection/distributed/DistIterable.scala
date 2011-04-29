package scala.collection.distributed

import api._
import api.dag._
import collection.generic.{GenericCompanion}
import mrapi.FSAdapter
import execution.{DCUtil, ExecutionPlan}
import _root_.io.CollectionsIO
import collection.immutable.{GenSeq, GenIterable, GenTraversable}

trait DistIterable[+T]
  extends GenIterable[T]
  with GenericDistTemplate[T, DistIterable]
  with DistIterableLike[T, DistIterable[T], Iterable[T]]
  with CollectionId {
  def seq = FSAdapter.valuesIterable[T](location)

  override def companion: GenericCompanion[DistIterable] with GenericDistCompanion[DistIterable] = DistIterable

  def stringPrefix = "DistIterable"

  lazy val sizeLongVal: Long = CollectionsIO.getCollectionMetaData(this).size

  def size = if (sizeLong > Int.MaxValue) throw new RuntimeException("Size is larger than MAX_INT!!!") else sizeLong.toInt

  def sizeLong = sizeLongVal

  def nonEmpty = size != 0

  override def isEmpty = sizeLong != 0

  def flatten[B >: T](collections: GenTraversable[DistIterable[B]]): DistIterable[T] = {
    val outDistColl = new DistColl[T](DCUtil.generateNewCollectionURI)
    val node = ExecutionPlan.addFlattenNode(
      new FlattenPlanNode(outDistColl, List(this) ++ collections)
    )
    ExecutionPlan.sendToOutput(node, outDistColl)
    ExecutionPlan.execute()
    outDistColl
  }

  def sgbr[S, K, T1, T2, That](by: (T) => Ordered[S] = nullOrdered,
                               key: (T, Emitter[T1]) => K = nullKey,
                               reduce: (T2, T1) => T2 = nullReduce)
                              (implicit sgbrResult: ToSGBRColl[T, K, T1, T2, That]): That = {

    if (by == nullOrdered && key == nullKey)
      throw new RuntimeException("At least one parameter must be specified!!")

    if (key == nullKey && reduce != nullReduce)
      throw new RuntimeException("In order to reduce, key function must be specified.")

    val result = sgbrResult.result(DCUtil.generateNewCollectionURI)

    var input: CollectionId = this
    var output = CollectionId(DCUtil.generateNewCollectionURI)

    if (by != nullOrdered) {
      ExecutionPlan.addPlanNode(input, new SortPlanNode[T, S](output, by))
      input = output
      output = CollectionId(DCUtil.generateNewCollectionURI)
    }

    if (key != nullKey) {
      ExecutionPlan.addPlanNode(output, new GroupByPlanNode(input, key))
      input = output
      output = CollectionId(DCUtil.generateNewCollectionURI)
    }

    if (reduce != nullReduce) {
      ExecutionPlan.addPlanNode(output, new CombinePlanNode(input, reduce))
      input = output
      output = CollectionId(DCUtil.generateNewCollectionURI)
    }

    sgbrResult.result(output.location)
  }


  def distDo(distOp: (T, UntypedEmitter, DistContext) => Unit, outputs: GenSeq[CollectionId]) = {
    val outDistColls = outputs.map(id => new DistColl[Any](id.location))
    val node = ExecutionPlan.addPlanNode(this, new DistDoPlanNode(this, distOp, outDistColls))
    outDistColls.foreach(coll => ExecutionPlan.sendToOutput(node, coll))
    ExecutionPlan.execute()
    outDistColls
  }

  protected[this] def parCombiner = throw new UnsupportedOperationException("Not implemented yet!!!")
}

/**$factoryInfo
 */
object DistIterable extends DistFactory[DistIterable] {
  implicit def canBuildFrom[T]: CanDistBuildFrom[Coll, T, DistIterable[T]] = new GenericCanDistBuildFrom[T]

  def newRemoteBuilder[T] = new IterableRemoteBuilder[T]

  def newBuilder[T] = new IterableRemoteBuilder[T]
}

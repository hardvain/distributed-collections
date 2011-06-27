package scala.collection.distributed

import api.{RecordNumber, DistContext, Emitter2, Emitter}
import scala.collection.generic.CanBuildFrom
import scala._
import collection.immutable
import collection.{GenTraversableOnce, GenIterableLike}
import execution.ExecutionPlan

import shared.DSECollection


trait DistIterableLike[+T, +Repr <: DistIterable[T], +Sequential <: immutable.Iterable[T] with GenIterableLike[T, Sequential]]
  extends GenIterableLike[T, Repr]
  with HasNewRemoteBuilder[T, Repr]
  with RichDistProcessable[T] {

  self: DistIterableLike[T, Repr, Sequential] =>

  def seq: Sequential

  protected[this] def bf2seq[S, That](bf: CanBuildFrom[Repr, S, That]) = new CanBuildFrom[Sequential, S, That] {
    def apply(from: Sequential) = bf.apply(from.asInstanceOf[Repr])

    def apply() = bf.apply()
  }

  protected[this] def newRemoteBuilder: RemoteBuilder[T, Repr]

  def repr: Repr = this.asInstanceOf[Repr]

  def hasDefiniteSize = true

  def canEqual(other: Any) = true

  def mkString(start: String, sep: String, end: String): String = seq.mkString(start, sep, end)

  def mkString(sep: String): String = seq.mkString("", sep, "")

  def mkString: String = seq.mkString

  override def toString = seq.mkString(stringPrefix + "(", ", ", ")")

  def map[S, That](f: T => S)(implicit bf: CanBuildFrom[Repr, S, That]): That = {
    val remoteBuilder = bf.asInstanceOf[CanDistBuildFrom[Repr, S, That]](repr)
    val collection = distDo((el: T, em: Emitter[S]) => em.emit(f(el)))
    remoteBuilder.result(collection)
  }

  def flatMap[S, That](f: (T) => GenTraversableOnce[S])(implicit bf: CanBuildFrom[Repr, S, That]): That = {
    val remoteBuilder = bf.asInstanceOf[CanDistBuildFrom[Repr, S, That]](repr)
    remoteBuilder.result(distDo((el: T, emitter: Emitter[S]) => f(el).foreach((v) => emitter.emit(v))))
  }

  def filter(p: T => Boolean): Repr = {
    val rb = newRemoteBuilder
    rb.uniquenessPreserved

    rb.result(distDo((el: T, em: Emitter[T]) => if (p(el)) em.emit(el)))
  }

  def filterNot(pred: (T) => Boolean) = filter(!pred(_))

  def groupBySeq[K](f: (T) => K): DistMap[K, immutable.GenIterable[T]] with DistCombinable[K, T] = groupBySort((v: T, em: Emitter[T]) => {
    em.emit(v);
    f(v)
  })

  def reduce[A1 >: T](op: (A1, A1) => A1) = if (isEmpty)
    throw new UnsupportedOperationException("empty.reduce")
  else {
    val result = groupBySort((v: T, emitter: Emitter[T]) => {
      emitter.emit(v);
      1
    }).combine((it: Iterable[T]) => it.reduce(op))
    ExecutionPlan.execute(result)
    result.toTraversable.head._2
  }

  def find(pred: (T) => Boolean) = if (isEmpty)
    None
  else {
    var found = false;
    val allResults = distDo((el: T, emitter: Emitter[(RecordNumber, T)], con: DistContext) =>
      if (!found && pred(el)) {
        emitter.emit((con.recordNumber, el))
        found = true;
      })
    ExecutionPlan.execute(allResults)
    val allResultsSeq = allResults.seq.toSeq
    if (allResultsSeq.size == 0)
      None
    else
    // sort by record number and pick first
      Some(allResultsSeq.toSeq.sorted(Ordering[RecordNumber].on[(RecordNumber, _)](_._1)).head._2)
  }

  def partition(pred: (T) => Boolean) = {
    val result = distDo((el: T, emitter: Emitter2[T, T], con: DistContext) => if (pred(el)) emitter.emit1(el) else emitter.emit2(el))
    val builder = newRemoteBuilder
    builder.uniquenessPreserved
    (builder.result(result._1), builder.result(result._2))
  }

  def collect[B, That](pf: PartialFunction[T, B])(implicit bf: CanBuildFrom[Repr, B, That]) = {
    val remoteBuilder = bf.asInstanceOf[CanDistBuildFrom[Repr, B, That]](repr)
    remoteBuilder.result(distDo((el, emitter) => if (pf.isDefinedAt(el)) emitter.emit(pf(el))))
  }

  def ++[B >: T, That](that: GenTraversableOnce[B])(implicit bf: CanBuildFrom[Repr, B, That]) = {
    val remoteBuilder = bf.asInstanceOf[CanDistBuildFrom[Repr, T, That]](repr)
    remoteBuilder.result(this.flatten(that.asInstanceOf[DistIterable[B]]))
  }

  def reduceOption[A1 >: T](op: (A1, A1) => A1) = if (isEmpty) None else Some(reduce(op))

  def count(p: (T) => Boolean) = 0

  def forall(pred: (T) => Boolean) = {
    var found = false
    distDo((el: T, em: Emitter[Boolean], context: DistContext) => if (!found && !pred(el)) {
      em.emit(true)
      found = true
    }
    ).size > 0
  }

  def fold[A1 >: T](z: A1)(op: (A1, A1) => A1) = if (!isEmpty) op(reduce(op), z) else z

  // TODO (VJ) implement with global cache
  def exists(pred: (T) => Boolean) = {
    var found = false
    distDo((el: T, em: Emitter[Boolean]) => if (!found && pred(el)) {
      em.emit(true)
      found = true
    }).size > 0
  }

  // Implemented with seq collection
  def toSet[A1 >: T] = seq.toSet

  def foldRight[B](z: B)(op: (T, B) => B) = seq.foldRight(z)(op)

  def :\[B](z: B)(op: (T, B) => B): B = foldRight(z)(op)

  def foldLeft[B](z: B)(op: (B, T) => B) = seq.foldLeft(z)(op)

  def /:[B](z: B)(op: (B, T) => B): B = foldLeft(z)(op)

  def reduceRightOption[B >: T](op: (T, B) => B) = seq.reduceRightOption(op)

  def reduceLeftOption[B >: T](op: (B, T) => B) = seq.reduceLeftOption(op)

  def reduceRight[B >: T](op: (T, B) => B) = seq.reduceRight(op)

  def toMap[K, V](implicit ev: <:<[T, (K, V)]) = seq.toMap

  def toSeq = seq.toSeq

  def toIterable = seq.toIterable

  def toTraversable = seq.toTraversable

  def toBuffer[A1 >: T] = seq.toBuffer

  def toIterator = iterator

  def toStream = seq.toStream

  def toIndexedSeq[A1 >: T] = seq.toIndexedSeq

  def toList = seq.toList

  def toArray[A1 >: T](implicit evidence$1: ClassManifest[A1]) = seq.toArray(evidence$1)

  def foreach[U](f: (T) => U) = seq.foreach(f)

  def iterator = seq.toIterable.iterator

  def minBy[B](f: (T) => B)(implicit cmp: Ordering[B]) = {
    val result = groupBySort((v: T, emitter: Emitter[T]) => {
      emitter.emit(v);
      1
    }).combine((it: Iterable[T]) => it.minBy(f)(cmp))

    ExecutionPlan.execute(result)

    result.seq.head._2
  }

  def maxBy[B](f: (T) => B)(implicit cmp: Ordering[B]) = {
    val result = groupBySort((v: T, emitter: Emitter[T]) => {
      emitter.emit(v);
      1
    }).combine((it: Iterable[T]) => it.maxBy(f)(cmp))

    ExecutionPlan.execute(result)

    result.seq.head._2
  }

  def max[A1 >: T](implicit ord: Ordering[A1]) = {
    val result = groupBySort((v: T, emitter: Emitter[T]) => {
      emitter.emit(v);
      1
    }).combine((it: Iterable[T]) => it.max(ord))

    ExecutionPlan.execute(result)

    result.toSeq.head._2
  }

  def min[A1 >: T](implicit ord: Ordering[A1]) = {
    val result = groupBySort((v: T, emitter: Emitter[T]) => {
      emitter.emit(v);
      1
    }).combine((it: Iterable[T]) => it.min(ord))

    ExecutionPlan.execute(result)

    result.seq.head._2
  }

  def product[A1 >: T](implicit num: Numeric[A1]) = {
    val result = groupBySort((v: T, emitter: Emitter[T]) => {
      emitter.emit(v);
      1
    }).combine((it: Iterable[T]) => it.product(num))

    ExecutionPlan.execute(result)

    result.seq.head._2
  }

  def sum[A1 >: T](implicit num: Numeric[A1]) = {
    val result = groupBySort((v: T, emitter: Emitter[T]) => {
      emitter.emit(v);
      1
    }).combine((it: Iterable[T]) => it.sum(num))

    ExecutionPlan.execute(result)

    result.seq.head._2
  }

  def copyToArray[B >: T](xs: Array[B]) = copyToArray(xs, 0)

  def copyToArray[B >: T](xs: Array[B], start: Int) = copyToArray(xs, start, xs.length - start)

  def copyToArray[B >: T](xs: Array[B], start: Int, len: Int) = seq.copyToArray(xs, start, len)

  def scanLeft[S, That](z: S)(op: (S, T) => S)(implicit bf: CanBuildFrom[Repr, S, That]) = throw new UnsupportedOperationException("Not implemented yet!!!")//seq.scanLeft(z)(op)(bf2seq(bf))

  def scanRight[S, That](z: S)(op: (T, S) => S)(implicit bf: CanBuildFrom[Repr, S, That]) = throw new UnsupportedOperationException("Not implemented yet!!!")//seq.scanRight(z)(op)(bf2seq(bf))

  // TODO (vj) optimize when partitioning is introduced
  // TODO (vj) for now use only shared collection. If needed introduce SharedMap
  def drop(n: Int) = {
    val rb = newRemoteBuilder
    rb.uniquenessPreserved

    // max of records sorted by file part
    val sizes = new DSECollection[(Long, Long)](
      Some(_.foldLeft((0L, 0L))((aggr, v) => (v._1, scala.math.max(aggr._2, v._2)))),
      Some(it => {
        val (fileParts: immutable.GenSeq[Long], records: immutable.GenSeq[Long]) = it.toSeq.sortWith(_._1 < _._1).unzip
        fileParts.zip(records.scanLeft(0L)(_ + _))
      })
    )

    rb.result(distDo((el: T, em: Emitter[(Long, T)], ctx: DistContext) => if (ctx.recordNumber.counter < n) {
      sizes.put((ctx.recordNumber.filePart, ctx.recordNumber.counter))
      em.emit((ctx.recordNumber.filePart, el))
    }).groupBySort((el: (Long, T), em: Emitter[T]) => {
      em.emit(el._2);
      el._1
    }).distDo((el: (Long, scala.collection.immutable.GenIterable[T]), em: Emitter[T], ctx: DistContext) => {
      val start = sizes.toMap.get(el._1).get
      var counter = 0
      el._2.foreach(v => {
        if (start + counter < n) em.emit(v)
        counter += 1
      })
    }))
  }

  def zipWithLongIndex[A1 >: T, That](implicit bf: CanDistBuildFrom[Repr, (A1, Long), That]) = {
    val rb = bf(repr)
    rb.uniquenessPreserved
    // TODO (vj) optimize when partitioning is introduced
    // TODO (vj) for now use only shared collection. If needed introduce SharedMap

    // max of records sorted by file part
    val sizes = new DSECollection[(Long, Long)](
      Some(_.foldLeft((0L, 0L))((aggr, v) => (v._1, scala.math.max(aggr._2, v._2)))),
      Some(it => {
        val (fileParts: immutable.GenSeq[Long], records: immutable.GenSeq[Long]) = it.toSeq.sortWith(_._1 < _._1).unzip
        fileParts.zip(records.scanLeft(0L)(_ + _))
      })
    )

    rb.result(distDo((el: A1, em: Emitter[(Long, A1)], ctx: DistContext) => {
      sizes.put((ctx.recordNumber.filePart, ctx.recordNumber.counter))
      em.emit((ctx.recordNumber.filePart, el))
    }).groupBySort((el: (Long, A1), em: Emitter[A1]) => {
      em.emit(el._2)
      el._1
    }).distDo((el: (Long, scala.collection.immutable.GenIterable[A1]), em: Emitter[(A1, Long)], ctx: DistContext) => {
      val start = sizes.toMap.get(el._1).get
      var counter = 0
      el._2.foreach(v => {
        em.emit((v, start + counter))
        counter += 1
      })
    }))
  }

  def zipWithIndex[A1 >: T, That](implicit bf: CanBuildFrom[Repr, (A1, Int), That]) = throw new UnsupportedOperationException("Not implemented yet!!!")

  def scan[B >: T, That](z: B)(op: (B, B) => B)(implicit cbf: CanBuildFrom[Repr, B, That]) = throw new UnsupportedOperationException("Not implemented yet!!!")

  def dropWhile(pred: (T) => Boolean) = throw new UnsupportedOperationException("Not implemented yet!!!")

  def span(pred: (T) => Boolean) = throw new UnsupportedOperationException("Not implemented yet!!!")

  def splitAt(n: Int) = throw new UnsupportedOperationException("Not implemented yet!!!")

  def takeWhile(pred: (T) => Boolean) = throw new UnsupportedOperationException("Not implemented yet!!!")

  def take(n: Int) = throw new UnsupportedOperationException("Not implemented yet!!!")

  def slice(unc_from: Int, unc_until: Int) = throw new UnsupportedOperationException("Not implemented yet!!!")

  def zipAll[B, A1 >: T, That](that: collection.GenIterable[B], thisElem: A1, thatElem: B)(implicit bf: CanBuildFrom[Repr, (A1, B), That]) = throw new UnsupportedOperationException("Not implemented yet!!!")

  def zip[A1 >: T, B, That](that: collection.GenIterable[B])(implicit bf: CanBuildFrom[Repr, (A1, B), That]) = throw new UnsupportedOperationException("Not implemented yet!!!")

  def sameElements[A1 >: T](that: collection.GenIterable[A1]) = throw new UnsupportedOperationException("Not implemented yet!!!")

  def aggregate[B](z: B)(seqop: (B, T) => B, combop: (B, B) => B) = throw new UnsupportedOperationException("Not implemented yet!!!")

  // Not Implemented Yet
  def groupBy[K](f: (T) => K)= throw new UnsupportedOperationException("Not implemented yet!!!")
}
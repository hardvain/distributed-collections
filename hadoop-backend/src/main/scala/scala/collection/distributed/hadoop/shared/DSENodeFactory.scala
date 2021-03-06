package scala.collection.distributed.hadoop.shared

import collection.distributed.api.shared._
import java.nio.ByteBuffer
import org.apache.hadoop.mapred.{Reporter}
import java.net.URI
import collection.distributed.api.RecordNumber

/**
 * @author Vojin Jovanovic
 */

object DSENodeFactory {

  def initializeNode(reporter: Reporter, data: (DistSideEffects with DSEProxy[_], Array[Byte])): Unit = {
    data._1.varType match {
      case CollectionType =>
        val uri = new URI(new String(data._2))
        data._1.asInstanceOf[DistBuilderProxy[Any, Any]].impl =
          new DistBuilderNode(uri, data._1.uid)
      case CounterType => {
        val buffer = ByteBuffer.allocate(8)
        buffer.put(data._2)
        data._1.asInstanceOf[DistCounterProxy].impl =
          new DistCounterNode(buffer.getLong(0), reporter.getCounter("DSECounter", data._1.uid.toString))
      }
      case RecordType => {
        data._1.asInstanceOf[DistRecordCounterProxy].impl = new DistRecordCounterNode(new RecordNumber())
      }
    }
  }
}
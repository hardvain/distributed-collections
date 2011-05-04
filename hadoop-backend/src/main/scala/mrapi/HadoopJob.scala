package mrapi

import java.net.URI
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.mapreduce.lib.input.{FileInputFormat}
import org.apache.hadoop.mapreduce.lib.output.{FileOutputFormat}
import scala.collection.distributed.api.AbstractJobStrategy
import org.apache.hadoop.filecache.DistributedCache
import java.util.UUID
import scala.collection.distributed.api.dag._
import scala.collection.mutable
import mutable.ArrayBuffer
import scala.collection.distributed.api.io.CollectionMetaData
import java.io.{ByteArrayOutputStream, ObjectOutputStream}
import org.apache.hadoop.fs.{PathFilter, FileSystem, Path}
import org.apache.hadoop.io.{NullWritable, BytesWritable}
import org.apache.hadoop.mapreduce.{Job}

/**
 * User: vjovanovic
 * Date: 3/21/11
 */

object HadoopJob extends AbstractJobStrategy {

  def execute(dag: ExPlanDAG, globalCache: mutable.Map[String, Any]) = {

    val optimizedDag = optimizePlan(dag)

    executeInternal(dag)

  }

  private def executeInternal(dag: ExPlanDAG) {

    // extract first builder and dag
    val mscrBuilder = new MapCombineShuffleReduceBuilder()
    val remainingExPlan = mscrBuilder.build(dag)

    // execute builder
//    val config: Configuration = new Configuration
//    val job = new Job(config, "TODO (VJ)")

//    mscrBuilder.configure(job)

    // serialize global cache
//    dfsSerialize(job, "global.cache", globalCache.toMap)

//    job.waitForCompletion(true)

    // fetch collection sizes and write them to DFS
//    mscrBuilder.output.foreach(out => storeCollectionsMetaData(job, out))

    // de-serialize global cache
    // TODO(VJ)

    // recurse over rest of the dag
//    executeInternal(remainingExPlan)
  }


  def storeCollectionsMetaData(job: Job, outputPlanNode: OutputPlanNode) = {
    val size = job.getCounters.findCounter("collections", "current").getValue
    // write metadata
    val metaDataPath = new Path(outputPlanNode.location.toString, "META")
    val baos = new ByteArrayOutputStream()
    val oos = new ObjectOutputStream(baos)
    oos.writeObject(new CollectionMetaData(size))
    oos.flush()
    oos.close()
    FSAdapter.writeToFile(job, metaDataPath, baos.toByteArray)
  }

//  def extractMSCR(queue: mutable.Queue[PlanNode], dag: ExPlanDAG): MapCombineShuffleReduceBuilder = {
//    val mscrBuilder = new MapCombineShuffleReduceBuilder()
//    var node: Option[PlanNode] = Some(queue.dequeue)
//    var mapPhase = true
//    while (node.isDefined) {
//      node.get match {
//        case value: InputPlanNode =>
//          mscrBuilder.input += value
//
//        case value: DistDoPlanNode[_] =>
//          if (mapPhase)
//            mscrBuilder.mapParallelDo = Some(value)
//          else
//            mscrBuilder.reduceParallelDo = Some(value)
//
//        case value: GroupByPlanNode[_, _, _] =>
//          mscrBuilder.groupBy = Some(value)
//          mapPhase = false
//
//        case value: CombinePlanNode[_, _, _, _] =>
//          mscrBuilder.combine = Some(value)
//          mapPhase = false
//
//        case value: SortPlanNode[_, _, _, _] =>
//          mscrBuilder.combine = Some(value)
//          mapPhase = false
//
//        case value: OutputPlanNode =>
//          mscrBuilder.output += value
//
//        case value: FlattenPlanNode =>
//          mscrBuilder.flatten = Some(value)
//          value.collections.foreach((col) => {
//
//            // TODO (VJ) fix this cast (will not work with views)
//            val inputNode = dag.getPlanNode(col).get.asInstanceOf[InputPlanNode]
//            mscrBuilder.input += inputNode
//            queue.dequeueFirst(_ == inputNode)
//
//          })
//      }
//      node = node.get.outEdges.headOption
//    }
//
//    mscrBuilder
//  }


  private def optimizePlan(dag: ExPlanDAG): ExPlanDAG = {
    // TODO (VJ) introduce optimizations
    dag
  }

  def dfsSerialize(job: Job, key: String, data: AnyRef) = {
    // place the closure in distributed cache
    val conf = job.getConfiguration
    val fs = FileSystem.get(conf)
    val hdfsPath = tmpPath()
    val hdfsos = fs.create(hdfsPath)
    val oos = new ObjectOutputStream(hdfsos)
    oos.writeObject(data)
    oos.flush()
    oos.close()

    val serializedDataURI = hdfsPath.toUri();
    conf.set(key, serializedDataURI.toString)
    DistributedCache.addCacheFile(serializedDataURI, conf)
  }

  private def tmpPath(): Path = {
    new Path("tmp/" + new URI(UUID.randomUUID.toString).toString)
  }

}

class MapCombineShuffleReduceBuilder {
  // new builder interface
  val mapperDag: ExPlanDAG = new ExPlanDAG()
  val combineDag: ExPlanDAG = new ExPlanDAG()
  val reduceDag: ExPlanDAG = new ExPlanDAG()

  // old interface
  var input: mutable.Set[InputPlanNode] = mutable.HashSet()
  var mapParallelDo: Option[ParallelDoPlanNode[_, _]] = None
  var groupBy: Option[GroupByPlanNode[_, _, _]] = None
  var combine: Option[CombinePlanNode[_, _, _, _]] = None
  var reduceParallelDo: Option[ParallelDoPlanNode[_, _]] = None
  var flatten: Option[FlattenPlanNode] = None
  var output: mutable.HashSet[OutputPlanNode] = mutable.HashSet()

  def build(dag: ExPlanDAG) = {
    // collect mapper phase (stopping at distDo and flatten)
    val startingNodes = new mutable.Queue() ++ dag.inputNodes
    val visited = new ArrayBuffer[PlanNode]()


    val matches = (n: PlanNode) => n match {
      case GroupByPlanNode(f) => false
      case SortPlanNode(f) => false
      case CombinePlanNode(f) => false
      case _ => true
    }



    val newDAG = new ExPlanDAG()

    def buildDag(node: PlanNode, newDAG: ExPlanDAG, visited: ArrayBuffer[PlanNode]): Unit = {
      if (matches(node)) {
        node match {
          case InputPlanNode(location) => newDAG.addInputNode(InputPlanNode(location))
          case _ =>

            node.inEdges.foreach(edge => {
              newDAG.getPlanNode(edge._1)
            })
        }

        visited += node
        node.outEdges.foreach(edge => {
          buildDag(edge._1, newDAG, visited)
        })
      } else {
        // add an output node to newDAG if not already present
      }


//      startingNodes.foreach((node: PlanNode) => buildDag(node, newDAG, visitedS))
      // link it to the parent in new dag
    }




  // collect combine phase (all except distDo and flatten)

  // collect reducer phase (only distDo)
}


def configure (job: Job) = {

// setting mapper and reducer classes and serializing closures


// old
/*if (mapParallelDo.isDefined) {
// serialize list of operation chains
HadoopJob.dfsSerialize(job, "distcoll.mapper.do", mapParallelDo.get.parOperation)
println("Map parallel do!!")
}

if (flatten.isDefined) {
HadoopJob.dfsSerialize(job, "distcoll.mapper.flatten", flatten.get.collections)
println("Flatten!!")
}

if (groupBy.isDefined) {
// serialize group by closure
HadoopJob.dfsSerialize(job, "distcoll.mapper.groupBy", groupBy.get.keyFunction)
println("GroupBy!!")
}

// set combiner
if (combine.isDefined) {
HadoopJob.dfsSerialize(job, "distcoll.mapreduce.combine", combine.get.op)
println("Combine!!")
}

// serialize reducer parallel closure
if (reduceParallelDo.isDefined) {
//serialize reduce parallel do
HadoopJob.dfsSerialize(job, "distcoll.reducer.do", reduceParallelDo.get.parOperation)
println("Reduce parallel do!!")
} */

// setting input and output and intermediate types
job.setMapOutputKeyClass (classOf[BytesWritable] )
job.setMapOutputValueClass (classOf[BytesWritable] )
job.setOutputKeyClass (classOf[NullWritable] )
job.setOutputValueClass (classOf[BytesWritable] )

QuickTypeFixScalaI0.setJobClassesBecause210SnapshotWillNot (job, groupBy.isDefined);

// set the input and output files for the job
//input.foreach ((in) => FileInputFormat.addInputPath (job, new Path (in.id.location.toString) ) )
//FileInputFormat.setInputPathFilter (job, classOf[MetaPathFilter] )

// TODO (VJ) fix the multiple outputs
//output.foreach ((out) => FileOutputFormat.setOutputPath (job, new Path (out.id.location.toString) ) )
}
}

class MetaPathFilter extends PathFilter {
  def accept(path: Path) = !path.toString.endsWith("META")
}
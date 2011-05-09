package scala.colleciton.distributed.hadoop

import collection.distributed.api.dag.{OutputPlanNode, ExPlanDAG}
import collection.distributed.api.ReifiedDistCollection
import org.apache.hadoop.mapred.JobConf

trait MSCRBuilder {

  def build(dag: ExPlanDAG): ExPlanDAG

  def configure(job: JobConf)

  def outputs : Traversable[ReifiedDistCollection]

}
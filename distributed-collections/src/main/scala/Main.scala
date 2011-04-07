import dcollections.api.Emitter
import java.net.URI
import scala.util.Random

/**
 * User: vjovanovic
 * Date: 3/21/11
 */

object Main {
  def main(args: Array[String]) = {
    val someValue = Random.nextInt.abs % 100

    val distributedSet = new dcollections.DistSet[Long](new URI("long-set"))
    val reduced = distributedSet.combineValues((v, emitter: Emitter[Long]) => {
      emitter.emit(v);
      1L
    },
      (agg: Long, v: Long) => agg + v
    )
    println("Reduced = " + reduced.toString)

    val distributedSet1 = new dcollections.DistSet[Long](new URI("long-set1"))
    println("Random Value = " + someValue)

    // value containing closures
    val generatedSet = distributedSet.map(_ + someValue)

    val flatten = generatedSet.flatten(List(distributedSet1))

    println(flatten.toString)
    //    println("Reduced value (SUM) =" + generatedSet.reduce((a: Long, b: Long) => a + b))
    //
    //    // test of printing
    //    println("Original set:")
    //    println(distributedSet.toString)
    //
    //    println("New original.map(_ + 1):")
    //    println(generatedSet.toString)
    //
    //
    //    // checking multiple operations
    //    println("Multiple operations original.map(_ * 123.123).filter(_ > 400):")
    //    println(generatedSet.map(_ * 123.123).filter(_ > 400).toString)
    //
    //
    //
    //    // test set property
    //    val noDuplicatesSet = distributedSet.map(_ % 2)
    //    println(noDuplicatesSet.toString)

  }
}
# Distributed Collections for Scala

Distributed Collections for Scala is a library for large scale data processing that uses different cluster computing frameworks as the back-end. Library inherits *Scala 2.9.1* collections generic interface enriched with additional methods like `join`, `reduceByKey` etc.
Currently the library uses only *Hadoop* as the back-end processing engine. However, we are aiming to extend the library to work with other frameworks like [*Spark*](http://www.spark-project.org ""), [*HaLoop*](http://code.google.com/p/haloop/ "") and [*Nephele*](http://www.stratosphere.eu/).
This library is still in early phases of development, many features are not functioning and it is still **UNUSABLE**. The project timline can be found [here](http://github.com/scala-incubator/distributed-collections/wiki/Milestones-and-Timeline).

## Build Instructions

Project is built by Simple Build Tool and can be packaged by invoking `sbt update package` from project root. Building is also available through Maven by invoking `mvn package`.
After packaging you need to copy the library and all dependencies to the $HADOOP-HOME/lib:

 * distributed-collections
 * backend-api
 * hadoop-backend
 * objenesis-1.2.jar
 * kryo-1.04-mod.jar
 * minlog-1.2.jar
 * reflectasm-1.01.jar
 * scala-library.jar

## Project Structure

Project consists of 4 modules:

 * distriubted-collections - Distributed Collections Library
 * hadoop-backend - back-end that executes collections operations on Hadoop MapReduce
 * beckend-api - api shared between the library and back-end components
 * benchmarks - Distributed Collections benchmarks

Project is being developed by IntelliJ IDEA 10.x and following parts of the project tree are for IDEA convenience purposes only:

  * .idea - project folder

## Usage

Using the library is very similar to using Scala collections and parallel collections with several key differences:

  * Collections are instantiated from the URI that points to the data
  * Many operations use Int data type which can easily overflow in the domain so long versions should be used instead
  * In the communication between the cluster nodes every piece of data is serialized and deserialized and the user must be careful not to accidentally transfer large parts of object graph

 For more detailed description see the [wiki page](http://github.com/scala-incubator/distributed-collections/wiki/Distributed-Collections-for-Scala "")

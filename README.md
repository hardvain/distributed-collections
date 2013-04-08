# Distributed Collections for Scala

**NOTE:** Development of Distributed Collections for Scala is discontinued. The repository will remain online for existing references.
The reasons for discontinuations are:

 * *Incompatibility with Scala Collections* - Distributed collections require different interface for collections.
Methods like `groupBy` return an `immutable.Map` which is not compatible with distributed collections. Furthermore,
in some cases data manifests, or similar constructs are needed for efficient serialization but those would require
significant changes in the common signature.

 * *Rich signature of Scala Collections* - The Scala collections hierarchy contains methods whose signature is 
intended for in-memory operation. By default Scala Collections eagerly execute the code which is for distributed
collections overly expensive. Furthermore, abstractions like `Map` and `Set` require a barrier after every
operation making them very slow even for basic operations like `map`. Finally, the Scala Collections expose
methods like `zip` and `zipWithIndex` which are used frequently but are expensive in the distributed case. Exposing
these methods would lure users into using them but their performance on large data would be unacceptably low.

 * *Use cases* - Abstractions like `Map` and `Set` are widely used in shared memory environment for fast lookups.
In case of distributed data this functionality is rarely needed and requires traversal of the whole data set.
However, maintenance of these abstractions in the distributed case is very high.


Distributed Collections for Scala is a library for large scale data processing that uses different cluster computing frameworks as the back-end. Library inherits *Scala 2.9.1* collections generic interface enriched with additional methods like `join`, `reduceByKey` etc.
Currently the library uses only *Hadoop* as the back-end processing engine. However, we are aiming to extend the library to work with other frameworks like [*Spark*](http://www.spark-project.org ""), [*HaLoop*](http://code.google.com/p/haloop/ "") and [*Nephele*](http://www.stratosphere.eu/).

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

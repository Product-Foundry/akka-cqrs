Akka CQRS
=========

[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/Product-Foundry/akka-cqrs?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge)
[![License](https://img.shields.io/:license-Apache%202-red.svg)](http://www.apache.org/licenses/LICENSE-2.0.txt)
[![Build Status](https://travis-ci.org/Product-Foundry/akka-cqrs.svg?branch=master)](https://travis-ci.org/Product-Foundry/akka-cqrs)
[![Codacy Badge](https://api.codacy.com/project/badge/grade/ed9ff4454c664bc5806a3bdc64584026)](https://www.codacy.com/app/Product-Foundry/akka-cqrs)
[![Codacy Badge](https://api.codacy.com/project/badge/coverage/ed9ff4454c664bc5806a3bdc64584026)](https://www.codacy.com/app/andrekampert/akka-cqrs)
[![Download](https://api.bintray.com/packages/productfoundry/maven/akka-cqrs/images/download.svg) ](https://bintray.com/productfoundry/maven/akka-cqrs/_latestVersion)

### Warning: This is a work in progress, API changes are likely


Dependency
----------

To include this library into your `sbt` project, add the following lines to your `build.sbt` file:

    resolvers += "Product-Foundry at bintray" at "http://dl.bintray.com/productfoundry/maven"

    libraryDependencies += "com.productfoundry" %% "akka-cqrs" % "0.2.0"

This version of `akka-cqrs` is built using Akka 2.4.3 and Scala 2.11.8.

Usage
-----

## Command side

### Entities

Entities are persistent objects that implement DDD concepts. There are different entity types:

##### Aggregates

Aggregates process commands, validate business rules and generate events. They act as a context boundary for a single
domain concept. Typically, the aggregate represents a single instance, rather than a group. All aggregates reply with a
message indicating the update result.

![Aggregate](doc/aggregate.png)

##### Process managers

Process managers subscribe to events to execute long running processes. They have durable state to track a single
process using a finite state machine implementation. A process can subscribe to events from different aggregates.

#### Creation

![Entity creation](doc/entity-creation.png)

## Query side

## Serialization

Our data structures are serialized using `protobuf`

```bash 
protoc -I ./core/src/main/protobuf/ --java_out=./core/src/main/java/ ./core/src/main/protobuf/*
```


Inspiration
-----------
- [Reactive DDD with Akka](http://pkaczor.blogspot.nl/2014/04/reactive-ddd-with-akka.html)
- [Simple event sourcing ](http://blog.zilverline.com/2012/07/04/simple-event-sourcing-introduction-part-1/)

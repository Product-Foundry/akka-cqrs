package com.productfoundry.akka.cqrs.project

import com.productfoundry.akka.cqrs.Commit

case class CommitCollector(commits: Vector[Commit] = Vector.empty) extends Projection[CommitCollector] {

  override def project(commit: Commit): CommitCollector = {
    copy(commits = commits :+ commit)
  }
}

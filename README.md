Thrift & Finagle proof of concept
=================================

Usage
---

### Starting the server
`sbt run`

### Starting the client
`sbt "project scroogeClient" "run <T> <Rmin> <Rmax> <Rfactor> <M>"`

Where T is timeout in mills, Rmin/Rmax is min/max latency in mills between retries which increases exponentially by a factor of Rfactor.
M is number of responses to wait for ignoring the rests; M <= N where N is the number of instantiated servers which are taken from configuration file.


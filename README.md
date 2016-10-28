Thrift & Finagle proof of concept
=================================

Usage
---

### Starting the server
`sbt run`

### Starting the client
`sbt "project scroogeClient" "run <T> <Rmin> <Rmax> <N> <M>"`

Where T is timeout in mills, Rmin/Rmax is min/max latency in mills between retries (increases exponentially by a factor of two).
N is number of total requests to send to server and M is number of responses to wait for ignoring the rests. M <= N

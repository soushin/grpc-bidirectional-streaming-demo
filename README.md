# bidrectional grpc stremaing demo

This repository just demo for using bidrectional grpc stremaing.


## Generate gRPC code for server and client

```
# Go client
$ protoc -I protobuf/ --go_out=plugins=grpc:protobuf/ protobuf/microservice.proto
```

```
# Java server
$ cp protobuf/microservice.proto server/grpc-server/src/main/proto/microservice.proto && cd server/grpc-server/ && gradle generateProto
```

## Run server and client, then publish queue

To start the server
```
$ cd ./server/grpc-server
$ gradle clean install
$ ./build/install/grpc-server/bin/microservice-server
```

To run the client
```
$ cd ./client
$ LOG=* go run clinet.go
```

Then publish queue from redis(port is 6379)
```
redis-cli
127.0.0.1:6379> PUBLISH my_queue '{"serviceName" : "division", "numbers" : [10, 2]}'
```

![demo](https://raw.githubusercontent.com/nsoushi/grpc-bidirectionalstreaming-demo/master/docs/demo.gif)
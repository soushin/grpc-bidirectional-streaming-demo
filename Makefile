all: client server

proto-client:
	@echo "--> Generating Go files"
	protoc -I protobuf/ --go_out=plugins=grpc:protobuf/ protobuf/microservice.proto
	@echo ""

proto-server:
	@echo "--> Generating Go files"
	cp protobuf/microservice.proto server/grpc-server/src/main/proto/microservice.proto && cd server/grpc-server/ && gradle generateProto
	@echo ""

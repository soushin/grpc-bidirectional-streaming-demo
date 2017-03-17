package main

import (
	"context"
	"encoding/json"
	"flag"
	"fmt"
	"github.com/azer/logger"
	pb "github.com/nsoushi/grpc-bidirectionalstreaming-demo/protobuf"
	"google.golang.org/grpc"
	"gopkg.in/redis.v5"
	"io"
	"time"
)

var (
	serverHost string
	serverPort int

	redisHost    string
	redisPort    int
	redisChannel string

	log         *logger.Logger
	requestLog  *logger.Logger
	responseLog *logger.Logger
)

type QueueMessage struct {
	ServiceName string `json:"serviceName"`
	Params      []int  `json:"numbers"`
}

func init() {
	flag.StringVar(&serverHost, "serverHost", "localhost", "serverHost")
	flag.IntVar(&serverPort, "serverPort", 8088, "serverPort")
	flag.StringVar(&redisHost, "redisHost", "localhost", "redisHost")
	flag.IntVar(&redisPort, "redisPort", 6379, "redisPort")
	flag.StringVar(&redisChannel, "redisChannel", "my_queue", "redisChannel")

	log = logger.New("app")
	requestLog = logger.New("Request ")
	responseLog = logger.New("Response")
}

func main() {

	// redis pub/sub connection
	pubSub, err := pubSubConnection()
	if err != nil {
		log.Error("Redis Connection error: %v", err)
	}
	defer pubSub.Close()

	// gRPC connection
	conn, err := grpc.Dial(fmt.Sprintf("%s:%d", serverHost, serverPort), grpc.WithInsecure())
	if err != nil {
		log.Error("Connection error: %v", err)
	}
	defer conn.Close()

	stream, err := pb.NewMicroServiceClient(conn).MicroService(context.Background())

	// request/response
	waitc := make(chan struct{})
	go func() {
		for {
			in, err := stream.Recv()
			if err == io.EOF {
				close(waitc)
				return
			}
			if err != nil {
				log.Error("Failed to receive a message : %v", err)
			}
			responseLog.Info("{serviceName:'%s', message:'%s', time:'%s'}", in.Name, in.Message, in.Time)
		}
	}()

	for {
		message, err := pubSub.ReceiveMessage()
		if err != nil {
			panic(err)
		}
		requests, err := getRequests(message)
		if err != nil {
			panic(err)
		}

		for _, request := range requests {

			requestLog.Info("{serviceName:'%s', message:'%s', time:'%s'}", request.Name, request.Message, request.Time)
			if err := stream.Send(&request); err != nil {
				log.Error("Failed to send a message: %v", err)
			}
		}
	}

	stream.CloseSend()
	<-waitc
}

func pubSubConnection() (*redis.PubSub, error) {
	client := redis.NewClient(&redis.Options{
		Addr: fmt.Sprintf("%s:%d", redisHost, redisPort),
	})
	pubSub, err := client.Subscribe(redisChannel)
	if err != nil {
		return nil, err
	}

	return pubSub, nil
}

func getRequests(msg *redis.Message) ([]pb.Request, error) {

	q := &QueueMessage{}
	m := &msg.Payload
	if err := json.Unmarshal([]byte(*m), q); err != nil {
		return nil, err
	}

	requestTime := fmt.Sprintf("%s", time.Now().Format("2006-01-02 15:04:05"))

	requests := []pb.Request{
		{q.ServiceName, fmt.Sprintf("%d", q.Params[0]), requestTime},
		{q.ServiceName, fmt.Sprintf("%d", q.Params[1]), requestTime},
	}

	return requests, nil
}

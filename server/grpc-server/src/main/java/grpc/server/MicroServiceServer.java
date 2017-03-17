package grpc.server;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import proto.MicroServiceGrpc;
import proto.Microservice;
import trikita.log.Log;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * @author nsoushi
 */
public class MicroServiceServer {

    private static final Logger logger = Logger.getLogger(MicroServiceServer.class.getName());

    private final int port = 8088;
    private final Server server;
    private final Map<Long, List<Microservice.Request>> routeNumber = new HashMap<>();
    private final Map<Long, List<Microservice.Response>> responses = new HashMap<>();

    private MicroServiceServer() {
        server = ServerBuilder.forPort(port).addService(new ServerImpl())
                .build();
    }

    private void start() throws IOException {
        server.start();
        logger.info("Server started, listening on " + port);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                System.err.println("*** shutting down gRPC server since JVM is shutting down");
                MicroServiceServer.this.stop();
                System.err.println("*** server shut down");
            }
        });
    }

    private void stop() {
        if (server != null) {
            server.shutdown();
        }
    }

    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    public static void main(String[] args) throws Exception {
        MicroServiceServer server = new MicroServiceServer();
        server.start();
        server.blockUntilShutdown();
    }

    private class ServerImpl extends MicroServiceGrpc.MicroServiceImplBase {
        @Override
        public StreamObserver<Microservice.Request> microService(final StreamObserver<Microservice.Response> responseObserver) {

            return new StreamObserver<Microservice.Request>() {
                public void onNext(Microservice.Request req) {
                    Long key = getTime(req);
                    Observable.just(req)
                            .subscribe(new Observer<Microservice.Request>() {

                                @Override
                                public void onSubscribe(Disposable d) {
                                    Log.i("Request", getRequestLog(req));
                                }

                                @Override
                                public void onNext(Microservice.Request request) {
                                    if (!routeNumber.containsKey(key)) {
                                        routeNumber.put(key, Arrays.asList(req));
                                    } else if (routeNumber.get(key).size() == 1) {

                                        Microservice.Request prevRequest = routeNumber.get(key).get(0);
                                        Integer leftTerm = Integer.parseInt(prevRequest.getMessage());
                                        Integer rightTerm = Integer.parseInt(req.getMessage());

                                        Integer quotient = leftTerm / rightTerm;
                                        Integer remainder = leftTerm % rightTerm;

                                        if (remainder == 0) {
                                            responses.putIfAbsent(key, Arrays.asList(
                                                    getResponse(req.getName(), String.format("quotient:%d", quotient))));
                                        } else {
                                            responses.putIfAbsent(key, Arrays.asList(
                                                    getResponse(req.getName(), String.format("quotient:%d", quotient)),
                                                    getResponse(req.getName(), String.format("remainder:%d", remainder))));
                                        }
                                    } else {
                                        Log.w(String.format("waring, unknown state. key:{%s}, value:{%s}", key, routeNumber.get(key)));
                                    }
                                }

                                @Override
                                public void onError(Throwable e) {
                                    Log.e(String.format("onError %s", e.getMessage()));
                                }

                                @Override
                                public void onComplete() {
                                    if (responses.containsKey(key)) {
                                        Observable.fromIterable(responses.get(key))
                                                .subscribe(res -> {
                                                    responseObserver.onNext(res);
                                                    Log.i("Response", getResponseLog(res));
                                                });
                                        routeNumber.remove(key);
                                        responses.remove(key);
                                    }
                                }
                            });
                }

                public void onError(Throwable t) {
                    logger.log(Level.WARNING, "microService cancelled");
                }

                public void onCompleted() {
                    responseObserver.onCompleted();
                }
            };
        }
    }

    private Microservice.Response getResponse(String serviceName, String message) {
        return Microservice.Response.newBuilder()
                .setName(serviceName)
                .setMessage(String.format(message))
                .setTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .build();
    }

    private Long getTime(Microservice.Request request) {
        return LocalDateTime.parse(request.getTime(),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private String getRequestLog(Microservice.Request req) {
        return String.format("{serviceName:'%s', message:'%s', time:'%s'}", req.getName(), req.getMessage(), req.getTime());
    }

    private String getResponseLog(Microservice.Response res) {
        return String.format("{serviceName:'%s', message:'%s', time:'%s'}", res.getName(), res.getMessage(), res.getTime());
    }
}

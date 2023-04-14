package io.skyrosforkafka;

import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RPCServer {

  private static final Logger logger = Logger.getLogger(
    RPCServer.class.getName()
  );

  private Server server;
  private static DurabilityServer durabilityServer;

  public RPCServer(DurabilityServer durabilityServer) {
    this.durabilityServer = durabilityServer;
  }

  public void start(int port) throws IOException {
    server =
      Grpc
        .newServerBuilderForPort(port, InsecureServerCredentials.create())
        .addService(new SkyrosKafkaImplement())
        .build()
        .start();
    logger.info("Server started, listening on " + port);
    Runtime
      .getRuntime()
      .addShutdownHook(
        new Thread() {
          @Override
          public void run() {
            // Use stderr here since the logger may have been reset by its JVM shutdown hook.
            System.err.println(
              "*** shutting down gRPC server since JVM is shutting down"
            );
            try {
              RPCServer.this.stop();
            } catch (InterruptedException e) {
              e.printStackTrace(System.err);
            }
            System.err.println("*** server shut down");
          }
        }
      );
  }

  private void stop() throws InterruptedException {
    if (server != null) {
      server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
    }
  }

  /**
   * Await termination on the main thread since the grpc library uses daemon threads.
   */
  public void blockUntilShutdown() throws InterruptedException {
    if (server != null) {
      server.awaitTermination();
    }
  }

  /**
   * Main launches the server from the command line.
   */
  public static void main(String[] args)
    throws IOException, InterruptedException {}

  static class SkyrosKafkaImplement
    extends SkyrosKafkaImplGrpc.SkyrosKafkaImplImplBase {

    public void put(
      PutRequest req,
      StreamObserver<PutResponse> responseObserver
    ) {
      logger.info("Got put request!" + req.getRequestId());

      PutResponse response = durabilityServer.putInDurability(req);
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }

    public void get(
      GetRequest req,
      StreamObserver<GetResponse> responseObserver
    ) {
      logger.info("Got get request!");

      GetResponse response = durabilityServer.getDataFromKafka(
        req.getTopic(),
        req.getNumRecords(),
        req.getTimeout(),
        responseObserver
      );
      if (response == null || response.getValue().equals("op_not_done")) {
        responseObserver.onCompleted();
      }
    }

    public StreamObserver<TrimRequest> trimLog(
      final StreamObserver<TrimResponse> responseObserver
    ) {
      logger.log(Level.INFO, "Got trim request!");

      return new StreamObserver<TrimRequest>() {
        long trimmedLogs;

        @Override
        public void onNext(TrimRequest trimRequest) {
          logger.info("here in next");
          if (durabilityServer.handleTrimRequest(trimRequest)) {
            trimmedLogs++;
          }
        }

        @Override
        public void onError(Throwable throwable) {
          logger.log(Level.WARNING, "Encountered error in trimming", throwable);
        }

        @Override
        public void onCompleted() {
          logger.log(Level.INFO, "here in completed");
          responseObserver.onNext(
            TrimResponse.newBuilder().setTrimCount(trimmedLogs).build()
          );
          logger.log(Level.INFO, " completed trim response");
        }
      };
    }
  }
}

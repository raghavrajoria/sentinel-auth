package sentinel;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.63.0)",
    comments = "Source: scoring.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class ScoringServiceGrpc {

  private ScoringServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "sentinel.ScoringService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<sentinel.Scoring.ScoreRequest,
      sentinel.Scoring.ScoreResponse> getScoreMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Score",
      requestType = sentinel.Scoring.ScoreRequest.class,
      responseType = sentinel.Scoring.ScoreResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<sentinel.Scoring.ScoreRequest,
      sentinel.Scoring.ScoreResponse> getScoreMethod() {
    io.grpc.MethodDescriptor<sentinel.Scoring.ScoreRequest, sentinel.Scoring.ScoreResponse> getScoreMethod;
    if ((getScoreMethod = ScoringServiceGrpc.getScoreMethod) == null) {
      synchronized (ScoringServiceGrpc.class) {
        if ((getScoreMethod = ScoringServiceGrpc.getScoreMethod) == null) {
          ScoringServiceGrpc.getScoreMethod = getScoreMethod =
              io.grpc.MethodDescriptor.<sentinel.Scoring.ScoreRequest, sentinel.Scoring.ScoreResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Score"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  sentinel.Scoring.ScoreRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  sentinel.Scoring.ScoreResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ScoringServiceMethodDescriptorSupplier("Score"))
              .build();
        }
      }
    }
    return getScoreMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static ScoringServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ScoringServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ScoringServiceStub>() {
        @java.lang.Override
        public ScoringServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ScoringServiceStub(channel, callOptions);
        }
      };
    return ScoringServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static ScoringServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ScoringServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ScoringServiceBlockingStub>() {
        @java.lang.Override
        public ScoringServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ScoringServiceBlockingStub(channel, callOptions);
        }
      };
    return ScoringServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static ScoringServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ScoringServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ScoringServiceFutureStub>() {
        @java.lang.Override
        public ScoringServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ScoringServiceFutureStub(channel, callOptions);
        }
      };
    return ScoringServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     */
    default void score(sentinel.Scoring.ScoreRequest request,
        io.grpc.stub.StreamObserver<sentinel.Scoring.ScoreResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getScoreMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service ScoringService.
   */
  public static abstract class ScoringServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return ScoringServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service ScoringService.
   */
  public static final class ScoringServiceStub
      extends io.grpc.stub.AbstractAsyncStub<ScoringServiceStub> {
    private ScoringServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ScoringServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ScoringServiceStub(channel, callOptions);
    }

    /**
     */
    public void score(sentinel.Scoring.ScoreRequest request,
        io.grpc.stub.StreamObserver<sentinel.Scoring.ScoreResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getScoreMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service ScoringService.
   */
  public static final class ScoringServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<ScoringServiceBlockingStub> {
    private ScoringServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ScoringServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ScoringServiceBlockingStub(channel, callOptions);
    }

    /**
     */
    public sentinel.Scoring.ScoreResponse score(sentinel.Scoring.ScoreRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getScoreMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service ScoringService.
   */
  public static final class ScoringServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<ScoringServiceFutureStub> {
    private ScoringServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ScoringServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ScoringServiceFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<sentinel.Scoring.ScoreResponse> score(
        sentinel.Scoring.ScoreRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getScoreMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_SCORE = 0;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final AsyncService serviceImpl;
    private final int methodId;

    MethodHandlers(AsyncService serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_SCORE:
          serviceImpl.score((sentinel.Scoring.ScoreRequest) request,
              (io.grpc.stub.StreamObserver<sentinel.Scoring.ScoreResponse>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          getScoreMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              sentinel.Scoring.ScoreRequest,
              sentinel.Scoring.ScoreResponse>(
                service, METHODID_SCORE)))
        .build();
  }

  private static abstract class ScoringServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    ScoringServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return sentinel.Scoring.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("ScoringService");
    }
  }

  private static final class ScoringServiceFileDescriptorSupplier
      extends ScoringServiceBaseDescriptorSupplier {
    ScoringServiceFileDescriptorSupplier() {}
  }

  private static final class ScoringServiceMethodDescriptorSupplier
      extends ScoringServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    ScoringServiceMethodDescriptorSupplier(java.lang.String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (ScoringServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new ScoringServiceFileDescriptorSupplier())
              .addMethod(getScoreMethod())
              .build();
        }
      }
    }
    return result;
  }
}

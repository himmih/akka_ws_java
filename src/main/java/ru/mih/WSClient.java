package ru.mih;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.ws.Message;
import akka.http.javadsl.model.ws.WebSocketRequest;
import akka.http.javadsl.model.ws.WebSocketUpgradeResponse;
import akka.japi.Pair;
import akka.stream.IOResult;
import akka.stream.Materializer;
import akka.stream.javadsl.*;
import akka.util.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

public class WSClient {

    private static final Logger logger = LoggerFactory.getLogger(WSClient.class);
    private static final String SERVER = "127.0.0.1";
    private static final int PORT = 8080;
    private final Materializer materializer;
    private final ActorSystem actorSystem;
    private final Http http;
    private static final Set appends = new HashSet();


    public WSClient() {
        this.actorSystem = ActorSystem.create("test_runner");
        this.materializer = Materializer.createMaterializer(actorSystem);
        this.http = Http.get(actorSystem);
        appends.add(StandardOpenOption.CREATE);
        appends.add(StandardOpenOption.APPEND);
    }

    public void run() throws ExecutionException, InterruptedException {

        final Sink<ByteString, CompletionStage<IOResult>> fileSink =
                FileIO.toPath(Paths.get("/tmp/ws.log"), appends);

        final Flow<Message, ByteString, NotUsed> flowToBytes =
                Flow.fromFunction((Message n) -> ByteString.fromString(n.asTextMessage().getStrictText()));

//        final Sink<Message, CompletionStage<Done>> printSink =
//                Sink.foreach((message) -> {
//                    if (message.isStrict()) {
//                        logger.info(message.asTextMessage().getStrictText());
//                    } else {
//                        message.asTextMessage().getStreamedText().runWith(Sink.foreach(x -> logger.info(x)),
//                                this.materializer);
//                    }
//                });

        final String url = "ws://" + SERVER +":" + PORT + "/ints";

        final Flow<Message, Message, CompletionStage<IOResult>> flow =
                    Flow.fromSinkAndSourceMat(
//                          printSink,
                            flowToBytes.toMat(fileSink, (a, b) -> b),
                          Source.<Message>maybe(),
//                            Source.<Message>empty(),
                            Keep.left()).async();

        final Source<Integer, NotUsed> numberSource = Source.range(1, 100000);
        numberSource.runWith(Sink.foreachAsync(100000, any -> {
                    Pair<CompletionStage<WebSocketUpgradeResponse>, CompletionStage<IOResult>> openSocket
                            = http.singleWebSocketRequest(WebSocketRequest.create(url), flow, materializer);
                    return openSocket.second().thenAccept(done -> logger.warn("Socket\\file closed!")); //.complete(Optional.empty());
//                            .first().thenAccept(upgradeCompletion -> Done.done())
                }
        ), this.actorSystem); //.thenAccept( any -> this.actorSystem.terminate());


    }

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        new WSClient().run();
    }
}

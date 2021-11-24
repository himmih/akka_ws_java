package ru.mih;

import akka.Done;
import akka.NotUsed;
import akka.actor.typed.Behavior;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.model.ws.Message;
import akka.http.javadsl.model.ws.TextMessage;
import akka.http.scaladsl.model.AttributeKeys;
import akka.japi.function.Function;
import akka.stream.javadsl.Flow;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.ActorSystem;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletionStage;

public class WSServer {
    private static final List NUMBERS = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

    static void startHttpServer(ActorSystem<?> system) {
        final Function<HttpRequest, HttpResponse> handler = request -> handleRequest(request);
        CompletionStage<ServerBinding> futureBinding =
            Http.get(system).newServerAt("localhost", 8080).bindSync(handler);

        futureBinding.whenComplete((binding, exception) -> {
            if (binding != null) {
                InetSocketAddress address = binding.localAddress();
                system.log().info("Server online at http://{}:{}/",
                    address.getHostString(),
                    address.getPort());
            } else {
                system.log().error("Failed to bind HTTP endpoint, terminating system", exception);
                system.terminate();
            }
        });
    }

    public static HttpResponse handleRequest(HttpRequest request) {
        if (request.getUri().path().equals("/ints")) {
            return request.getAttribute(AttributeKeys.webSocketUpgrade())
                    .map(upgrade -> {
                        Source<Message, NotUsed> numbersThrottling =
                                Source.from(NUMBERS)
                                        .map(i -> TextMessage.create(String.valueOf(i)))
                                        .throttle(1, Duration.ofSeconds(30));
                        Flow<Message, Message, CompletionStage<Done>> greeterFlow =
                        Flow.fromSinkAndSourceMat(
                                Sink.ignore(),
                                numbersThrottling,
                                Keep.left());
                        HttpResponse response = upgrade.handleMessagesWith(greeterFlow);
                        return response;
                    })
                    .orElse(
                            HttpResponse.create().withStatus(StatusCodes.BAD_REQUEST)
                                    .withEntity("Expected WebSocket request")
                    );
        } else {
            return HttpResponse.create().withStatus(404);
        }
    }

    public static void main(String[] args) throws Exception {
        Behavior<NotUsed> rootBehavior = Behaviors.setup(context -> {
            startHttpServer(context.getSystem());
            return Behaviors.empty();
        });

        ActorSystem.create(rootBehavior, "ws_server");
    }

}



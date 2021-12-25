package ru.bmstu.rapirapr.azmetov.akka;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.Query;
import akka.japi.Pair;
import akka.pattern.Patterns;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.asynchttpclient.Dsl.asyncHttpClient;

public class ResponseTimeApp {
    public static final String ACTOR_SYSTEM_NAME = "ResponseTimeActorSystem";
    public static final Integer HTTP_PORT = 8080;
    public static final String HTTP_HOST = "localhost";
    public static final String SERVER_STARTED_MESSAGE = "Сервер запущен";
    public static final String URL_QUERY_PARAMETER_ALIAS = "url";
    public static final String COUNT_QUERY_PARAMETER_ALIAS = "count";
    public static final String HTTP_RESPONSE_PREFIX = "Среднее время ответа: ";

    public static void main(String[] args) throws Exception {
        ActorSystem system = ActorSystem.create(ACTOR_SYSTEM_NAME);
        ActorRef actor = system.actorOf(Props.create(StoreActor.class));
        ActorMaterializer materializer = ActorMaterializer.create(system);
        final Http http = Http.get(system);
        final Flow<HttpRequest, HttpResponse, NotUsed> routeFlow = createRoute(actor, materializer);
        final CompletionStage<ServerBinding> binding = http.bindAndHandle(
                routeFlow,
                ConnectHttp.toHost(HTTP_HOST, HTTP_PORT),
                materializer
        );
        System.out.println(SERVER_STARTED_MESSAGE);
        System.in.read();
        binding.thenCompose(ServerBinding::unbind).thenAccept(unbound -> system.terminate());
    }

    private static Flow<HttpRequest, HttpResponse, NotUsed> createRoute(ActorRef actor, ActorMaterializer materializer) {
        return Flow.of(HttpRequest.class)
                .map((request) -> {
                    final Query query = request.getUri().query();
                    return new Pair<>(
                            query.get(URL_QUERY_PARAMETER_ALIAS).get(),
                            Integer.parseInt(query.get(COUNT_QUERY_PARAMETER_ALIAS).get())
                    );
                })
                .mapAsync(1, pair -> {
                    System.out.println(pair);
                    CompletionStage<Object> savedResult = Patterns.ask(actor, new Message(pair.first()), Duration.ofSeconds(5));
                    return savedResult.thenCompose(result -> {
                        if ((Integer) result >= 0) {
                            return CompletableFuture.completedFuture(new TestResult(
                                    pair.first(),
                                    (Integer) result
                            ));
                        }
                        final Flow<Pair<String, Integer>, Integer, NotUsed> routeFlow = Flow.<Pair<String, Integer>>create()
                                .mapConcat(_pair -> new ArrayList<>(Collections.nCopies(_pair.second(), _pair.first())))
                                .mapAsync(pair.second(), url -> {
                                    long start = System.currentTimeMillis();
                                    asyncHttpClient().prepareGet(url).execute();
                                    long end = System.currentTimeMillis();
                                    return CompletableFuture.completedFuture((int) (end - start));
                                });
                        return Source.single(pair)
                                .via(routeFlow)
                                .toMat(Sink.fold(0, Integer::sum), Keep.right())
                                .run(materializer)
                                .thenApply(sum -> new TestResult(pair.first(), sum / pair.second(), true));
                    });
                })
                .map(request -> {
                    if (request.getIsNew()) {
                        actor.tell(request, ActorRef.noSender());
                    }
                    return HttpResponse.create().withEntity(HTTP_RESPONSE_PREFIX + request.getTime());
                });
    }
}

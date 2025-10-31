package se.fk.github.rimfrost.vardavhusdjur;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings("deprecation")
@Testcontainers
public class ContainerVahSmokeTest {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static KafkaContainer kafka;
    private static GenericContainer<?> vah;
    private static final String kafkaImage = TestConfig.get("kafka.image");
    private static final String vahImage = TestConfig.get("vah.image");
    private static final int vahPort = TestConfig.getInt("vah.port");
    private static final String rtfRequestTopic = TestConfig.get("rtf.request.topic");
    private static final String rtfResponseTopic = TestConfig.get("rtf.response.topic");
    private static final int topicTimeout = TestConfig.getInt("topic.timeout");
    private static final String networkAlias = TestConfig.get("network.alias");
    private static final String smallryeKafkaBootstrapServers = networkAlias + ":9092";

    @BeforeAll
    static void setupContainers() {
       Network network = Network.newNetwork();
       kafka = new KafkaContainer(DockerImageName.parse(kafkaImage)
               .asCompatibleSubstituteFor("apache/kafka"))
               .withNetwork(network)
               .withNetworkAliases(networkAlias);
       kafka.start();
       try {
           createTopic(rtfRequestTopic, 1, (short) 1);
           createTopic(rtfResponseTopic, 1, (short) 1);
       } catch (Exception e) {
           throw new RuntimeException("Failed to create Kafka topics", e);
       }

        //noinspection resource
        vah = new GenericContainer<>(DockerImageName.parse(vahImage))
               .withNetwork(network)
               .withEnv("MP_MESSAGING_CONNECTOR_SMALLRYE_KAFKA_BOOTSTRAP_SERVERS", smallryeKafkaBootstrapServers)
               .withExposedPorts(vahPort)
               .waitingFor(Wait.forHttp("/vah").forStatusCode(200));
       vah.start();
    }

    static void createTopic(String topicName, int numPartitions, short replicationFactor) throws Exception {
        String bootstrap = kafka.getBootstrapServers().replace("PLAINTEXT://", "");
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);

        try (AdminClient admin = AdminClient.create(props)) {
            NewTopic topic = new NewTopic(topicName, numPartitions, replicationFactor);
            admin.createTopics(List.of(topic)).all().get();
            System.out.println("Created topic: " + topicName);
        }
    }

    @AfterAll
    static void tearDown() {
        if (vah != null) vah.stop();
        if (kafka != null) kafka.stop();
    }

    private String readKafkaRequestMessage() {
        String bootstrap = kafka.getBootstrapServers().replace("PLAINTEXT://", "");
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + System.currentTimeMillis());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList(rtfRequestTopic));
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(5));

            if (records.isEmpty()) {
                throw new IllegalStateException("No Kafka message received on topic " + rtfRequestTopic);
            }
            return records.iterator().next().value();
        }
    }

    private CompletableFuture<Void> startKafkaResponder(ExecutorService executor, String jsonData) {
        return CompletableFuture.runAsync(() -> {
            try (KafkaConsumer<String, String> consumer = createConsumer()) {
                consumer.subscribe(Collections.singletonList(rtfRequestTopic));
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));
                if (records.isEmpty()) {
                    throw new IllegalStateException("No Kafka message received on " + rtfRequestTopic);
                }
                String message = records.iterator().next().value();
                Map<String, Object> req = mapper.readValue(message, new TypeReference<>() {});
                String processId = (String) req.get("processId");
                String personNummer = (String) req.get("pnr");

                String responseJson = """
                {
                  "processId": "%s",
                  "personNummer": "%s",
                  "rattTillForsakring": "Ja"
                }
                """.formatted(processId, personNummer);

                //sendKafkaResponse(processId, personNummer, "Ja");
                sendKafkaResponse2(req, rtfResponseTopic, jsonData);

                System.out.printf("Sent mock Kafka response for processId=%s%n", processId);
            } catch (Exception e) {
                throw new RuntimeException("Kafka responder failed", e);
            }
        }, executor);
    }

    private void sendKafkaResponse(String processId, String personNummer, String rattTillForsakring) throws Exception {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            String responseJson = """
                {
                  "processId": "%s",
                  "personNummer": "%s",
                  "rattTillForsakring": "%s"
                }
                """.formatted(processId, personNummer, rattTillForsakring);

            ProducerRecord<String, String> record =
                    new ProducerRecord<>(rtfResponseTopic, processId, responseJson);
            record.headers().add("correlationId", processId.getBytes(StandardCharsets.UTF_8));

            producer.send(record).get();
        }
    }

    private void sendKafkaResponse2(Map<String, Object> request, String topic, String dataJson) throws Exception {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            String eventJson = """
            {
              "specversion": "%s",
              "id": "%s",
              "source": "%s",
              "type": "%s",
              "time": "%s",
              "kogitoparentprociid": "%s",
              "kogitorootprocid": "%s",
              "kogitoproctype": "%s",
              "kogitoprocinstanceid": "%s",
              "kogitoprocist": "%s",
              "kogitoprocversion": "%s",
              "kogitorootprociid": "%s",
              "kogitoprocid": "%s",
              "kogitoprocrefid": "%s",
              "data": "%s"
            }
            """.formatted(
                    request.get("specversion"),             // specversion
                    request.get("id"),                      // id
                    request.get("source"),                  // source
                    topic,                                  // type
                    Instant.now().toString(),               // time
                    request.get("kogitoparentprociid"),     // kogitoparentprociid
                    request.get("kogitorootprocid"),        // kogitorootprocid
                    request.get("kogitoproctype"),          // kogitoproctype
                    request.get("kogitoprocinstanceid"),    // kogitoprocinstanceid
                    request.get("kogitoprocist"),           // kogitoprocist
                    request.get("kogitoprocversion"),       // kogitoprocversion
                    request.get("kogitorootprociid"),       // kogitorootprociid
                    request.get("kogitoprocid"),            // kogitoprocid
                    request.get("kogitoprocinstanceid"),    // kogitoprocrefid  SIC!
                    dataJson                                // raw JSON string for the "data" field
            );

            ProducerRecord<String, String> record = new ProducerRecord<>(topic, (String) request.get("processId"), eventJson);
            // Add CloudEvent/Kogito-style headers for convenience
// Should we add this ??
//            record.headers().add("correlationId", processId.getBytes(StandardCharsets.UTF_8));
//            record.headers().add("ce_specversion", "1.0".getBytes(StandardCharsets.UTF_8));
//            record.headers().add("ce_type", eventType.getBytes(StandardCharsets.UTF_8));
//            record.headers().add("ce_source", "vah.rtf.service".getBytes(StandardCharsets.UTF_8));

            producer.send(record).get();
        }
    }


    private KafkaConsumer<String, String> createConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + System.currentTimeMillis());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        return new KafkaConsumer<>(props);
    }

    @ParameterizedTest
    @CsvSource({
            "19990101-0123, Ja"
    })
    void testVahWorkflow(String personNummer, String expectedResult) throws Exception {
        // Start background Kafka responder
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CompletableFuture<Void> responder = startKafkaResponder(executor);
        // Send REST request to start BPMN workflow
        String vahEndpoint = String.format("http://%s:%d/vah", vah.getHost(), vah.getMappedPort(vahPort));
        var vahBody = String.format("{\"pnr\":\"%s\"}", personNummer);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(vahEndpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(vahBody))
                .build();
        @SuppressWarnings("resource")
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        // Wait for kafka responder to complete
        responder.get(topicTimeout, TimeUnit.SECONDS);

        // Verify kafka message produced by VAH
        String kafkaMsg = readKafkaRequestMessage();
        Map<String, Object> kafkaMap = mapper.readValue(kafkaMsg, new TypeReference<>() {});
        assertEquals(personNummer, kafkaMap.get("pnr"));
        // Verify api response
        assertEquals(201, response.statusCode());
        Map<String, Object> responseMap = mapper.readValue(response.body(), new TypeReference<>() {});
        assertEquals(kafkaMap.get("processId"), responseMap.get("id"));
        assertEquals(expectedResult, responseMap.get("result"));
        assertEquals(personNummer, responseMap.get("pnr"));

    }
}

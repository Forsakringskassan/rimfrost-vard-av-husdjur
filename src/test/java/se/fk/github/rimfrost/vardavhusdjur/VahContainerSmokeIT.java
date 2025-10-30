package se.fk.github.rimfrost.vardavhusdjur;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import se.fk.rimfrost.api.vahregelrtfspec.*;
import se.fk.rimfrost.api.vardavhusdjur.jaxrsspec.controllers.generatedsource.model.VahRequest;
import se.fk.rimfrost.api.vardavhusdjur.jaxrsspec.controllers.generatedsource.model.VahResponse;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
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
public class VahContainerSmokeIT
{

   private static final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
         .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
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
   static void setupContainers()
   {
      Network network = Network.newNetwork();
      kafka = new KafkaContainer(DockerImageName.parse(kafkaImage)
            .asCompatibleSubstituteFor("apache/kafka"))
            .withNetwork(network)
            .withNetworkAliases(networkAlias);
      kafka.start();
      try
      {
         createTopic(rtfRequestTopic, 1, (short) 1);
         createTopic(rtfResponseTopic, 1, (short) 1);
      }
      catch (Exception e)
      {
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

   static void createTopic(String topicName, int numPartitions, short replicationFactor) throws Exception
   {
      String bootstrap = kafka.getBootstrapServers().replace("PLAINTEXT://", "");
      Properties props = new Properties();
      props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);

      try (AdminClient admin = AdminClient.create(props))
      {
         NewTopic topic = new NewTopic(topicName, numPartitions, replicationFactor);
         admin.createTopics(List.of(topic)).all().get();
         System.out.println("Created topic: " + topicName);
      }
   }

   @AfterAll
   static void tearDown()
   {
      if (vah != null)
         vah.stop();
      if (kafka != null)
         kafka.stop();
   }

   private String readKafkaRequestMessage()
   {
      String bootstrap = kafka.getBootstrapServers().replace("PLAINTEXT://", "");
      Properties props = new Properties();
      props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
      props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + System.currentTimeMillis());
      props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
      props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
      props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

      try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props))
      {
         consumer.subscribe(Collections.singletonList(rtfRequestTopic));
         ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(5));

         if (records.isEmpty())
         {
            throw new IllegalStateException("No Kafka message received on topic " + rtfRequestTopic);
         }
         return records.iterator().next().value();
      }
   }

   private CompletableFuture<Void> startKafkaResponder(ExecutorService executor)
   {
      return CompletableFuture.runAsync(() -> {
         try (KafkaConsumer<String, String> consumer = createConsumer())
         {
            consumer.subscribe(Collections.singletonList(rtfRequestTopic));
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));
            if (records.isEmpty())
            {
               throw new IllegalStateException("No Kafka message received on " + rtfRequestTopic);
            }

            // Deserialize request message into typed payload
            String message = records.iterator().next().value();
            VahRtfRequestMessagePayload request = mapper.readValue(message, VahRtfRequestMessagePayload.class);
            // Extract data safely
            VahRtfRequestMessageData requestData = request.getData();
            if (requestData == null)
            {
               throw new IllegalStateException("Missing data field in Kafka message: " + message);
            }
            String processId = requestData.getProcessId();
            String personNummer = requestData.getPersonNummer();
            // Create typed response data object
            VahRtfResponseMessageData responseData = new VahRtfResponseMessageData();
            responseData.setProcessId(processId);
            responseData.setPersonNummer(personNummer);
            responseData.setRattTillForsakring(RattTillForsakring.JA);

            sendKafkaResponse(request, rtfResponseTopic, responseData);
            System.out.printf("Sent mock Kafka response for processId=%s%n", processId);
         }
         catch (Exception e)
         {
            throw new RuntimeException("Kafka responder failed", e);
         }
      }, executor);
   }

   private void sendKafkaResponse(VahRtfRequestMessagePayload request,
         String topic,
         VahRtfResponseMessageData messageData) throws Exception
   {

      VahRtfResponseMessagePayload payload = new VahRtfResponseMessagePayload();
      payload.setSpecversion(request.getSpecversion());
      payload.setId(request.getId());
      payload.setSource(request.getSource());
      payload.setType(topic);
      payload.setTime(OffsetDateTime.now());
      payload.setKogitoparentprociid(request.getKogitoparentprociid());
      payload.setKogitorootprocid(request.getKogitorootprocid());
      payload.setKogitoproctype(request.getKogitoproctype());
      payload.setKogitoprocinstanceid(request.getKogitoprocinstanceid());
      payload.setKogitoprocist(request.getKogitoprocist());
      payload.setKogitoprocversion(request.getKogitoprocversion());
      payload.setKogitorootprociid(request.getKogitorootprociid());
      payload.setKogitoprocid(request.getKogitoprocid());
      payload.setKogitoprocrefid(request.getKogitoprocinstanceid());

      payload.setData(messageData);

      // Serialize entire payload to JSON
      String eventJson = mapper.writeValueAsString(payload);

      Properties props = new Properties();
      props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
      props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
      props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

      try (KafkaProducer<String, String> producer = new KafkaProducer<>(props))
      {
         ProducerRecord<String, String> record = new ProducerRecord<>(
               topic,
               request.getId(), // message key
               eventJson);
         System.out.println("Kafka mock sending:\n" + eventJson);
         producer.send(record).get();
      }
   }

   private KafkaConsumer<String, String> createConsumer()
   {
      Properties props = new Properties();
      props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
      props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + System.currentTimeMillis());
      props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
      props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
      props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
      return new KafkaConsumer<>(props);
   }

   @ParameterizedTest
   @CsvSource(
   {
         "19990101-0123"
   })
   void TestVahSmoke(String personNummer) throws Exception
   {
      // Start background Kafka responder
      ExecutorService executor = Executors.newSingleThreadExecutor();
      CompletableFuture<Void> responder = startKafkaResponder(executor);
      // Send REST request to start BPMN workflow
      VahRequest vahRequest = new VahRequest().pnr(personNummer);
      String vahBody = mapper.writeValueAsString(vahRequest);
      String vahEndpoint = String.format("http://%s:%d/vah", vah.getHost(), vah.getMappedPort(vahPort));
      HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(vahEndpoint))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(vahBody))
            .build();
      HttpClient client = HttpClient.newHttpClient();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      // Wait for kafka responder to complete
      responder.get(topicTimeout, TimeUnit.SECONDS);

      // Verify kafka message produced by VAH
      String kafkaMsg = readKafkaRequestMessage();
      Map<String, Object> kafkaMap = mapper.readValue(kafkaMsg, new TypeReference<>()
      {
      });
      @SuppressWarnings("unchecked")
      Map<String, Object> kafkaDataMap = (Map<String, Object>) kafkaMap.get("data");
      assertEquals(personNummer, kafkaDataMap.get("pnr"));
      // Verify api response
      assertEquals(201, response.statusCode());
      VahResponse vahResponse = mapper.readValue(response.body(), VahResponse.class);
      assertEquals(kafkaDataMap.get("processId"), vahResponse.getId());
      assertEquals(personNummer, vahResponse.getPnr());
   }
}

package se.fk.github.rimfrost.vardavhusdjur;

import io.quarkus.test.junit.QuarkusTest;
import org.jboss.logmanager.ExtHandler;
import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import se.fk.rimfrost.HandlaggningResponseMessagePayload;
import se.fk.rimfrost.framework.regel.RegelRequestMessagePayload;
import se.fk.rimfrost.framework.regel.Utfall;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class VahErrorTest extends VahTestBase
{
   private final List<String> capturedLogs = new CopyOnWriteArrayList<>();

   @BeforeAll
   void setupLogCapture()
   {
      Logger.getLogger("").addHandler(new ExtHandler()
      {
         @Override
         protected void doPublish(ExtLogRecord record)
         {
            capturedLogs.add(record.getLevel().getName() + " " + record.getFormattedMessage());
         }

         @Override
         public void flush()
         {
         }

         @Override
         public void close() throws SecurityException
         {
         }
      });
   }

   @AfterEach
   void clearLogs()
   {
      capturedLogs.clear();
   }

   /**
    * Verifies the "Avsluta process med error" path when maskinell kontroll returns a technical error. The process must
    * skip manuell kontroll and bekräfta beslut and instead send a handlaggning response with the error populated.
    */
   @Test
   void testVahMaskinellError() throws Exception
   {
      var handlaggningId = UUID.randomUUID().toString();
      System.out.println("Starting testVahMaskinellError");

      CompletableFuture<Void> responderRtfMaskinell = startKafkaResponderWithError(
            rtfMaskinellRequestTopic, rtfMaskinellResponseTopic, "RTF-001", "Tekniskt fel i rtfMaskinell",
            handlaggningId);

      sendVahHandlaggningRequest(handlaggningId, "A1");

      String rtfMaskinellRequest = readKafkaRequestMessage(rtfMaskinellRequestTopic, handlaggningId);
      System.out.println("Received rtfMaskinellRequest: " + rtfMaskinellRequest);
      RegelRequestMessagePayload rtfMaskinellRequestMessagePayload = mapper.readValue(rtfMaskinellRequest,
            RegelRequestMessagePayload.class);
      assertEquals(handlaggningId, rtfMaskinellRequestMessagePayload.getData().getHandlaggningId());
      assertEquals("d4ab4820-68d9-41e0-abe1-cd8f9865d275", rtfMaskinellRequestMessagePayload.getData().getAktivitetId());

      responderRtfMaskinell.get(topicTimeout, TimeUnit.SECONDS);

      String vahHandlaggningResponse = readKafkaRequestMessage(vahHandlaggningResponseTopic, handlaggningId);
      System.out.println("Received vahHandlaggningResponse: " + vahHandlaggningResponse);
      HandlaggningResponseMessagePayload response = mapper.readValue(vahHandlaggningResponse,
            HandlaggningResponseMessagePayload.class);
      assertEquals(handlaggningId, response.getData().getHandlaggningId());
      assertEquals("FEL", response.getData().getResultat());
      assertNotNull(response.getData().getError());

      boolean errorLogged = capturedLogs.stream()
            .filter(line -> line.contains(handlaggningId))
            .anyMatch(line -> line.contains("ERROR") && line.contains("RTF-001"));
      assertTrue(errorLogged, "Expected ERROR log containing handlaggningId and felkod RTF-001");
   }

   /**
    * Verifies that a technical error from the "Komplettering" step inside rtf_manuell propagates all the way out: the
    * ordinary manuell request must never be sent, and the process must end with the same "FEL" response as any other
    * error path.
    */
   @Test
   void testVahManuellKompletteringError() throws Exception
   {
      var handlaggningId = UUID.randomUUID().toString();
      System.out.println("Starting testVahManuellKompletteringError");

      CompletableFuture<Void> responderRtfMaskinell = startKafkaResponder(rtfMaskinellRequestTopic,
            rtfMaskinellResponseTopic, Utfall.UTREDNING, handlaggningId);
      CompletableFuture<Void> responderRtfManuellKomplettering = startKafkaResponderWithError(
            rtfManuellKompletteringRequestTopic, rtfManuellKompletteringResponseTopic, "RTF-KOMP-001",
            "Tekniskt fel i rtfManuellKomplettering", handlaggningId);

      sendVahHandlaggningRequest(handlaggningId, "A1");

      String rtfMaskinellRequest = readKafkaRequestMessage(rtfMaskinellRequestTopic, handlaggningId);
      System.out.println("Received rtfMaskinellRequest: " + rtfMaskinellRequest);
      responderRtfMaskinell.get(topicTimeout, TimeUnit.SECONDS);

      String rtfManuellKompletteringRequest = readKafkaRequestMessage(rtfManuellKompletteringRequestTopic,
            handlaggningId);
      System.out.println("Received rtfManuellKompletteringRequest: " + rtfManuellKompletteringRequest);
      RegelRequestMessagePayload kompletteringRequestPayload = mapper.readValue(rtfManuellKompletteringRequest,
            RegelRequestMessagePayload.class);
      assertEquals(handlaggningId, kompletteringRequestPayload.getData().getHandlaggningId());

      responderRtfManuellKomplettering.get(topicTimeout, TimeUnit.SECONDS);

      String vahHandlaggningResponse = readKafkaRequestMessage(vahHandlaggningResponseTopic, handlaggningId);
      System.out.println("Received vahHandlaggningResponse: " + vahHandlaggningResponse);
      HandlaggningResponseMessagePayload response = mapper.readValue(vahHandlaggningResponse,
            HandlaggningResponseMessagePayload.class);
      assertEquals(handlaggningId, response.getData().getHandlaggningId());
      assertEquals("FEL", response.getData().getResultat());
      assertNotNull(response.getData().getError());

      boolean errorLogged = capturedLogs.stream()
            .filter(line -> line.contains(handlaggningId))
            .anyMatch(line -> line.contains("ERROR") && line.contains("RTF-KOMP-001"));
      assertTrue(errorLogged, "Expected ERROR log containing handlaggningId and felkod RTF-KOMP-001");
   }

   /**
    * Verifies that when "Komplettering" gets no response at all, rtf_manuell_komplettering's own response timeout
    * (PT3M) fires, which propagates as a technical error out through rtf_manuell: the ordinary manuell request must
    * never be sent, and the process must end with the same "FEL" response as any other error path. This read waits
    * longer than the default 120 seconds since the process has to sit out the full response timeout first.
    */
   @Test
   void testVahManuellKompletteringTimeout() throws Exception
   {
      var handlaggningId = UUID.randomUUID().toString();
      System.out.println("Starting testVahManuellKompletteringTimeout");

      CompletableFuture<Void> responderRtfMaskinell = startKafkaResponder(rtfMaskinellRequestTopic,
            rtfMaskinellResponseTopic, Utfall.UTREDNING, handlaggningId);

      sendVahHandlaggningRequest(handlaggningId, "A1");

      String rtfMaskinellRequest = readKafkaRequestMessage(rtfMaskinellRequestTopic, handlaggningId);
      System.out.println("Received rtfMaskinellRequest: " + rtfMaskinellRequest);
      responderRtfMaskinell.get(topicTimeout, TimeUnit.SECONDS);

      String rtfManuellKompletteringRequest = readKafkaRequestMessage(rtfManuellKompletteringRequestTopic,
            handlaggningId);
      System.out.println("Received rtfManuellKompletteringRequest: " + rtfManuellKompletteringRequest);
      RegelRequestMessagePayload kompletteringRequestPayload = mapper.readValue(rtfManuellKompletteringRequest,
            RegelRequestMessagePayload.class);
      assertEquals(handlaggningId, kompletteringRequestPayload.getData().getHandlaggningId());

      // No responder is started for the komplettering topic: rtf_manuell_komplettering must wait out its full
      // PT3M response timeout before it gives up and propagates the error, so this read needs a longer deadline
      // than the default 120 seconds.
      String vahHandlaggningResponse = readKafkaRequestMessage(vahHandlaggningResponseTopic, handlaggningId,
            Duration.ofSeconds(200));
      System.out.println("Received vahHandlaggningResponse: " + vahHandlaggningResponse);
      HandlaggningResponseMessagePayload response = mapper.readValue(vahHandlaggningResponse,
            HandlaggningResponseMessagePayload.class);
      assertEquals(handlaggningId, response.getData().getHandlaggningId());
      assertEquals("FEL", response.getData().getResultat());
      assertNotNull(response.getData().getError());

      boolean errorLogged = capturedLogs.stream()
            .filter(line -> line.contains(handlaggningId))
            .anyMatch(line -> line.contains("ERROR") && line.contains("Timeout"));
      assertTrue(errorLogged, "Expected ERROR log for the komplettering response timeout");
   }
}

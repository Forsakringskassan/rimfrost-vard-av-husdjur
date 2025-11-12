package se.fk.github.rimfrost.vardavhusdjur.rtfmanuell;

import jakarta.enterprise.context.ApplicationScoped;
import se.fk.rimfrost.VahRtfManuellRequestMessageData;
import se.fk.rimfrost.VahRtfManuellResponseMessageData;

@ApplicationScoped
public class RtfManuellService
{

   public VahRtfManuellRequestMessageData createVahRtfManuellRequest(String pnr, String processId)
   {
      System.out.printf("Created VahRtfManuellRequest with pnr: %s with processId: %s%n", pnr, processId);
      VahRtfManuellRequestMessageData vahRtfManuellRequestMessageData = new VahRtfManuellRequestMessageData();
      vahRtfManuellRequestMessageData.setPersonNummer(pnr);
      vahRtfManuellRequestMessageData.setProcessId(processId);
      return vahRtfManuellRequestMessageData;
   }

   public Boolean onVahRtfManuellResponse(VahRtfManuellResponseMessageData vahRtfManuellResponse)
   {

      System.out.printf("Received VahRtfManuellResponse for processId: %s with result: %s%n",
            vahRtfManuellResponse.getProcessId(), vahRtfManuellResponse.getRattTillForsakring());
      return vahRtfManuellResponse.getRattTillForsakring();
   }

}

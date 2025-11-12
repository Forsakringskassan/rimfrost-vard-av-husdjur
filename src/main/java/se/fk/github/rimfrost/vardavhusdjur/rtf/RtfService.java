package se.fk.github.rimfrost.vardavhusdjur.rtf;

import jakarta.enterprise.context.ApplicationScoped;
import se.fk.rimfrost.RattTillForsakring;
import se.fk.rimfrost.VahRtfRequestMessageData;
import se.fk.rimfrost.VahRtfResponseMessageData;

@ApplicationScoped
public class RtfService
{

   public RattTillForsakring onVahRtfResponse(VahRtfResponseMessageData response)
   {
      System.out.print(response.toString());
      System.out.printf("Received VahRtfResponse for processId: %s with rattTillForsakring: %s%n",
            response.getProcessId(),
            response.getRattTillForsakring().toString());
      return response.getRattTillForsakring();
   }

   public VahRtfRequestMessageData createVahRtfRequest(String pnr, String processId)
   {
      System.out.printf("Created VahRtfRequest with pnr: %s with processId: %s%n", pnr, processId);
      VahRtfRequestMessageData requestMessageData = new VahRtfRequestMessageData();
      requestMessageData.setProcessId(processId);
      requestMessageData.setPersonNummer(pnr);
      return requestMessageData;
   }

}

package se.fk.github.rimfrost.vardavhusdjur.rtf;

import jakarta.enterprise.context.ApplicationScoped;
import se.fk.github.rimfrost.vardavhusdjur.rtf.model.VahRtfRequest;
import se.fk.github.rimfrost.vardavhusdjur.rtf.model.VahRtfResponse;

@ApplicationScoped
public class RtfService {

   public boolean onVahRtfResponse(VahRtfResponse response)
   {
      System.out.println(
            String.format("Received VahRtfResponse for processId: %s with result: %s", response.getProcessId(), response.getResult()));
      return response.getResult();
   }
   
   public VahRtfRequest createVahRtfRequest(String pnr, String processId)
   {
      System.out.println(String.format("Created VahRtfRequest with pnr: %s with processId: %s", pnr, processId));
      return new VahRtfRequest(processId, pnr);
   }
    
}

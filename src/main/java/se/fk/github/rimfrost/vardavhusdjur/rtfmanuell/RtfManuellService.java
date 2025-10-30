package se.fk.github.rimfrost.vardavhusdjur.rtfmanuell;

import jakarta.enterprise.context.ApplicationScoped;
import se.fk.github.rimfrost.vardavhusdjur.rtfmanuell.model.VahRtfManuellRequest;
import se.fk.github.rimfrost.vardavhusdjur.rtfmanuell.model.VahRtfManuellResponse;

@ApplicationScoped
public class RtfManuellService {

    
   public VahRtfManuellRequest createVahRtfManuellRequest(String pnr, String processId)
   {
      System.out.println(String.format("Created VahRtfManuellRequest with pnr: %s with processId: %s", pnr, processId));
      return new VahRtfManuellRequest(processId, pnr);
   }

   public boolean onVahRtfManuellResponse(VahRtfManuellResponse vahRtfManuellResponse)
   {
      System.out.println(
            String.format("Received VahRtfManuellResponse for processId: %s with result: %s", vahRtfManuellResponse.getProcessId(), vahRtfManuellResponse.getResult()));
      return vahRtfManuellResponse.getResult();
   }
    
}

package se.fk.github.rimfrost.vardavhusdjur;

import java.util.UUID;

public class VahRtfRequest
{

   private UUID processId;
   private String pnr;

   public VahRtfRequest()
   {
   }

   public VahRtfRequest(UUID processId, String pnr)
   {
      this.processId = processId;
      this.pnr = pnr;
   }

   public UUID getProcessId()
   {
      return processId;
   }

   public void setProcessId(UUID processId)
   {
      this.processId = processId;
   }

   public String getPnr()
   {
      return pnr;
   }

   public void setPnr(String pnr)
   {
      this.pnr = pnr;
   }

}

package se.fk.github.rimfrost.vardavhusdjur.rtf.model;

public class VahRtfRequest
{
   private String processId;
   private String pnr;

   public VahRtfRequest()
   {
   }

   public VahRtfRequest(String processId, String pnr)
   {
      super();
      this.processId = processId;
      this.pnr = pnr;
   }

   public String getProcessId()
   {
      return processId;
   }

   public void setProcessId(String processId)
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

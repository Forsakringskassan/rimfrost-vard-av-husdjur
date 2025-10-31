package se.fk.github.rimfrost.vardavhusdjur.rtf.model;

public class VahRtfResponse
{
   private String processId;
   private boolean hasArbetsgivare;
   private boolean isBokford;

   public VahRtfResponse()
   {
   }

   public VahRtfResponse(String processId, boolean hasArbetsgivare, boolean isBokford)
   {
      super();
      this.processId = processId;
      this.hasArbetsgivare = hasArbetsgivare;
      this.isBokford = isBokford;
   }

   public String getProcessId()
   {
      return processId;
   }

   public void setProcessId(String processId)
   {
      this.processId = processId;
   }

   public boolean getHasArbetsgivare()
   {
      return hasArbetsgivare;
   }

   public void setHasArbetsgivare(boolean hasArbetsgivare)
   {
      this.hasArbetsgivare = hasArbetsgivare;
   }

   public boolean getIsBokford()
   {
      return isBokford;
   }

   public void getIsBokford(boolean isBokford)
   {
      this.isBokford = isBokford;
   }
}

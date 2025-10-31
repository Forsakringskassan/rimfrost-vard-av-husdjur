package se.fk.github.rimfrost.vardavhusdjur.rtf.model;

public class VahRtfResponse
{
   private String processId;
   private boolean result;

   public VahRtfResponse()
   {
   }

   public VahRtfResponse(String processId, boolean result)
   {
      super();
      this.processId = processId;
      this.result = result;
   }

   public String getProcessId()
   {
      return processId;
   }

   public void setProcessId(String processId)
   {
      this.processId = processId;
   }

   public boolean getResult()
   {
      return result;
   }

   public void setResult(boolean result)
   {
      this.result = result;
   }
}

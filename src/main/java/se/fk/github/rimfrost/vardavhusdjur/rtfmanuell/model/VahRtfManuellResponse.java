package se.fk.github.rimfrost.vardavhusdjur.rtfmanuell.model;

public class VahRtfManuellResponse {
    
   private String processId;
   private boolean result;

   public VahRtfManuellResponse()
   {
   }

   public VahRtfManuellResponse(String processId, boolean result)
   {
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
        return this.result;
    }

    public void setResult(boolean result)
    {
        this.result = result;
    }
}

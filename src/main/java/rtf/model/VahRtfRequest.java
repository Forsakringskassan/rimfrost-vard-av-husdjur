<<<<<<< HEAD:src/main/java/vah/VahRtfRequest.java
package se.fk.github.rimfrost.vardavhusdjur;
=======
package rtf.model;
>>>>>>> 806e941 (feat: add rtf and rtfmanuell as subprocesses to vah process):src/main/java/rtf/model/VahRtfRequest.java


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

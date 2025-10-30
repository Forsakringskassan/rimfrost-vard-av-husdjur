package se.fk.github.rimfrost.vardavhusdjur;

import org.kie.kogito.internal.process.runtime.KogitoProcessContext;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class VahService
{

   public String startProcess(String pnr, KogitoProcessContext context)
   {
      var processId = context.getProcessInstance().getId();
      System.out.println(String.format("Started vård av husdjur process for pnr %s with processId %s", pnr, processId));
      return processId;
   }

   public void informAboutDecision(String pnr, String processId)
   {
      System.out.println(
            String.format("Vård av husdjur application for pnr %s with processId %s finished with success!", pnr, processId));
   }

   public void registerDecline(String pnr, String processId)
   {
      System.out.println(String.format("Vård av husdjur application for pnr %s with processId %s is declined!", pnr, processId));
   }

}

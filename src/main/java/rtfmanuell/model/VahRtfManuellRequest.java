package rtfmanuell.model;


public class VahRtfManuellRequest {
    
    private String processId;
    private String pnr;

    public VahRtfManuellRequest()
    {
    }

    public VahRtfManuellRequest(String processId, String pnr)
    {
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

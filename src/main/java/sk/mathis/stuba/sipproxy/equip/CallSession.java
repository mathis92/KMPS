/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package sk.mathis.stuba.sipproxy.equip;

import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.header.CallIdHeader;
import javax.sip.message.Request;

/**
 *
 * @author martinhudec
 */
public class CallSession {

    private Registration callerReg;
    private Registration calleeReg;
    private String state = "invReceived";
    private CallIdHeader callIdHeader;
    private Server sipServer;

    public CallSession(Registration callerReg, Registration calleeReg) {
        this.calleeReg = calleeReg;
        this.callerReg = callerReg;
    }

    public void getRequest(RequestEvent requestEvent) {
        try {
            switch (state) {
                case "invReceived": {
                    callIdHeader = (CallIdHeader) requestEvent.getRequest().getHeader(CallIdHeader.NAME);
                    Request forwardingRequest = (Request)requestEvent.getRequest().clone();
                    
                    state = "invCompleted";
                    break;
                }
                case "invCompleted": {
                    break;
                }
                case "connected": {
                    break;
                }
                case "done": {
                    break;
                }

            }

        } catch (NullPointerException ex) {
            ex.printStackTrace();
        }
    }

    public void getResponse(ResponseEvent responseEvent) {
        try {
            switch (state) {
                case "invCompleted": {

                break;
                }
            }
        } catch (NullPointerException ex) {
            ex.printStackTrace();
        }
    }
   public void rewriteRequestHeader(){
       
   }


}

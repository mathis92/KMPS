/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package sk.mathis.stuba.sipproxy.equip;

import gov.nist.javax.sip.clientauthutils.DigestServerAuthenticationHelper;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sip.DialogTerminatedEvent;
import javax.sip.IOExceptionEvent;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.TimeoutEvent;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.header.ViaHeader;
import javax.sip.message.Request;

/**
 *
 * @author martinhudec
 */
public class SipListener implements javax.sip.SipListener {

    private Server sipServer;
    private Users usrs = new Users();

    UserDevice dev = usrs.getUsersList().get(0);
    private DigestServerAuthenticationHelper digestServerAuthHelper;

    public SipListener(Server sipServer) {
        this.sipServer = sipServer;
        try {
            digestServerAuthHelper = new DigestServerAuthenticationHelper();
        } catch (NoSuchAlgorithmException ex) {
            Logger.getLogger(SipListener.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void processRequest(RequestEvent requestEvent) {
        try {
            Registration reg;
            if (requestEvent.getRequest().getMethod().equals(Request.REGISTER)) {
                reg = this.findRegistration(requestEvent);
                System.out.println(reg);
                if (reg != null) {
                    reg.register(requestEvent);
                } else {
                    reg = new Registration(sipServer);
                    sipServer.getRegistrationList().add(reg);
                    reg.register(requestEvent);
                }
            }
        } catch (NullPointerException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void processResponse(ResponseEvent responseEvent
    ) {
    }

    @Override
    public void processTimeout(TimeoutEvent timeoutEvent
    ) {
    }

    @Override
    public void processIOException(IOExceptionEvent exceptionEvent
    ) {
    }

    @Override
    public void processTransactionTerminated(TransactionTerminatedEvent transactionTerminatedEvent
    ) {
    }

    @Override
    public void processDialogTerminated(DialogTerminatedEvent dialogTerminatedEvent
    ) {
    }

    public Registration findRegistration(RequestEvent requestEvent) {
        try {
          
            ViaHeader vheader = (ViaHeader) requestEvent.getRequest().getHeader("via");
            System.out.println(sipServer.getRegistrationList().size() + " " + sipServer.getRegistrationList().get(0).getDev().getName());
            for (Registration registr : sipServer.getRegistrationList()) {
                if (registr.getDev().getHost().equals(vheader.getHost()) && registr.getDev().getPort().equals(vheader.getPort())) {
                    return registr;
                }
            }

        } catch (NullPointerException ex) {
            ex.printStackTrace();
        }
        return null;
    }

}

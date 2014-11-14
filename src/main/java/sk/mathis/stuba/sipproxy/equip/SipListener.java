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

    private DigestServerAuthenticationHelper digestServerAuthHelper;

    public SipListener(Server sipServer) {
        this.sipServer = sipServer;

    }

    @Override
    public void processRequest(RequestEvent requestEvent) {

        Registration reg;
        if (requestEvent.getRequest().getMethod().equals(Request.REGISTER)) {
            reg = findRegistration(requestEvent);
            if (reg != null) {
                System.out.println(reg);
                reg.register(requestEvent);
            } else {
                System.out.println("reg not found");
                reg = new Registration(sipServer);
                sipServer.getRegistrationList().add(reg);
                reg.register(requestEvent);

            }
        }
        if (requestEvent.getRequest().getMethod().equals(Request.INVITE)) {
            System.out.println(requestEvent.getRequest().toString());
            reg = findRegistration(requestEvent);
            if (reg != null) {
                reg.createCall(requestEvent);
            } else {
                System.out.println("neexistuje zariadenie");
            }
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
        System.out.println("idem hladat registraciu ");
        try {
            ViaHeader vheader = (ViaHeader) requestEvent.getRequest().getHeader("via");
            for (Registration registr : sipServer.getRegistrationList()) {
                System.out.println(registr.getRegHost() + " -> " + vheader.getHost() + " | " + registr.getRegPort() + " -> " + vheader.getPort());
                if (registr.getRegHost().equals(vheader.getHost()) && registr.getRegPort().equals(vheader.getPort())) {
                    return registr;
                }
            }

        } catch (NullPointerException ex) {
        }
        return null;
    }
//0903750657 topolsky
}

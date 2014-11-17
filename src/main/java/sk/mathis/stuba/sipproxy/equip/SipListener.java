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
import javax.sip.header.CallIdHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Request;
import org.slf4j.LoggerFactory;

/**
 *
 * @author martinhudec
 */
public class SipListener implements javax.sip.SipListener {

    private Server sipServer;
    private Users usrs = new Users();
    private org.slf4j.Logger logger = LoggerFactory.getLogger(SipListener.class);

    private DigestServerAuthenticationHelper digestServerAuthHelper;

    public SipListener(Server sipServer) {
        this.sipServer = sipServer;

    }

    @Override
    public void processRequest(RequestEvent requestEvent) {
        logger.info("INCOMING " + requestEvent.getRequest().getMethod());
        try {
            Registration reg = null;
            if (requestEvent.getRequest().getMethod().equals(Request.REGISTER)) {
                reg = findRegistration(requestEvent);
                if (reg != null) {
                    reg.register(requestEvent);
                } else {
                    logger.info("registracia sa nenasla");
                    logger.info("vytvaram novu registraciu");

                    reg = new Registration(sipServer);
                    sipServer.getRegistrationList().add(reg);
                    reg.register(requestEvent);

                }
            }
            if (requestEvent.getRequest().getMethod().equals(Request.INVITE)) {
                logger.info(requestEvent.getRequest().toString());
                reg = findRegistration(requestEvent);
                if (reg != null) {
                    reg.createCall(requestEvent);
                } else {
                    logger.info("volane zariadenie neexistuje");
                }
            }
            if (requestEvent.getRequest().getMethod().equals(Request.ACK)) {
                logger.info("mam ACK na requeste " + requestEvent.getRequest().toString());
                reg = findRegistration(requestEvent);
                if (reg != null) {
                    reg.createCall(requestEvent);
                }
            }
            if (requestEvent.getRequest().getMethod().equals(Request.BYE)) {
                reg = findRegistration(requestEvent);
                logger.info("mam BYE na requeste " + requestEvent.getRequest().toString());
                if(reg != null){
                    logger.info("reg not null BYE processing");
                    reg.createCall(requestEvent);
                }
            }
            if(requestEvent.getRequest().getMethod().equals(Request.CANCEL)){
               logger.info("mam CANCEL na requeste " + requestEvent.getRequest().toString());
                reg = findRegistration(requestEvent);
               if(reg != null){
                   logger.info("reg not null CANCEL processing");
                   reg.createCall(requestEvent);
               }
            }
        } catch (NullPointerException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void processResponse(ResponseEvent responseEvent) {
        logger.info("zachytil som response ");
        CallSession cs = findSession(responseEvent);
        if (cs != null) {
            cs.responseReceived(responseEvent);
        }
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

    public CallSession findSession(ResponseEvent responseEvent) {
        logger.info("vyhladavam session ");
        for (CallSession cs : sipServer.getCallSessionList()) {
            logger.info("call id header finding " + cs.getCallIdHeader() + " -> " + responseEvent.getResponse().getHeader(CallIdHeader.NAME));
            if (cs.getCallIdHeader().equals((CallIdHeader) responseEvent.getResponse().getHeader(CallIdHeader.NAME))) {
                logger.info("nasiel som session ");

                return cs;
            }
        }
        return null;
    }

    public CallSession findSession(RequestEvent requestEvent) {
        logger.info("vyhladavam session ");
        for (CallSession cs : sipServer.getCallSessionList()) {
            logger.info("call id header finding " + cs.getCallIdHeader() + " -> " + requestEvent.getRequest().getHeader(CallIdHeader.NAME));
            if (cs.getCallIdHeader().equals((CallIdHeader) requestEvent.getRequest().getHeader(CallIdHeader.NAME))) {
                logger.info("nasiel som session ");

                return cs;
            }
        }
        return null;
    }

    public Registration findRegistration(RequestEvent requestEvent) {
        logger.info("vyhladavam registraciu");
        try {
            ViaHeader vheader = (ViaHeader) requestEvent.getRequest().getHeader("via");
            for (Registration registr : sipServer.getRegistrationList()) {

                logger.info(registr.getRegHost() + " -> " + vheader.getHost() + " | " + registr.getRegPort() + " -> " + vheader.getPort());

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

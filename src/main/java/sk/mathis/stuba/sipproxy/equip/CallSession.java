/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package sk.mathis.stuba.sipproxy.equip;

import com.sun.javafx.scene.control.skin.VirtualFlow;
import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.header.CSeq;
import gov.nist.javax.sip.header.UserAgent;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sip.ClientTransaction;
import javax.sip.InvalidArgumentException;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipException;
import javax.sip.address.Address;
import javax.sip.address.SipURI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.UserAgentHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;
import org.slf4j.LoggerFactory;

/**
 *
 * @author martinhudec
 */
public class CallSession {

    private final Registration callerReg;
    private final Registration calleeReg;
    private String state = "invReceived";
    private CallIdHeader callIdHeader;
    private final Server sipServer;
    private Request sessionInviteRequest;
    private ServerTransaction sessionInviteServerTransaction;
    private ClientTransaction sessionInviteClientTransaction;
    private Response sessionOkResponse;
    private String sessionBranch;
    private final org.slf4j.Logger logger;

    public CallSession(Registration callerReg, Registration calleeReg, Server sipServer, CallIdHeader callIdHeader) {
        this.logger = LoggerFactory.getLogger(CallSession.class);
        this.sipServer = sipServer;
        this.calleeReg = calleeReg;
        logger.info("callee reg " + calleeReg.getDev().getHost() + " " + calleeReg.getDev().getExtension());
        this.callerReg = callerReg;
        logger.info("caller reg " + callerReg.getDev().getHost() + " " + callerReg.getDev().getExtension());
        this.callIdHeader = callIdHeader;
    }

    public void requestReceived(RequestEvent requestEvent) {
        
        
        
        try {
            switch (state) {
                case "invReceived": {
                    logger.info("FORWARDING IN ->>>>>>>>>>>>>> " + requestEvent.getRequest().toString());
                    callIdHeader = (CallIdHeader) requestEvent.getRequest().getHeader(CallIdHeader.NAME);
                    logger.info("callidHeader first invite " + callIdHeader);
                    sessionInviteServerTransaction = this.sipServer.getST(requestEvent);
                    sessionInviteRequest = requestEvent.getRequest();

                    Request forwardingRequest = (Request) requestEvent.getRequest().clone();
                    logger.info("vytvoril som forwarding request");

                    forwardingRequest = rewriteRequestHeader(forwardingRequest);
                    logger.info("REWRITED REQUEST " + forwardingRequest.toString());
                    sessionBranch = sipServer.createBranch();

                    forwardRequest(forwardingRequest);
                    returnCallerTrying(requestEvent.getRequest());

                    state = "invCompleted";
                    break;
                }
                case "invCompleted": {
                    if (requestEvent.getRequest().getMethod().equals(Request.ACK)) {
                        logger.info("able to send ACK");
                        resendAck();
                    }
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

    public void resendAck(){
        try {
            Request ack = sessionInviteClientTransaction.getDialog().createAck(((CSeqHeader)sessionOkResponse.getHeader(CSeqHeader.NAME)).getSeqNumber());
            logger.info("generated ack " + ack.toString());
            sessionInviteClientTransaction.getDialog().sendAck(ack);
        } catch (InvalidArgumentException | SipException ex) {
            Logger.getLogger(CallSession.class.getName()).log(Level.SEVERE, null, ex);
        }catch (NullPointerException np){
            np.printStackTrace();
        }
    }
    
    
    public void responseReceived(ResponseEvent responseEvent) {
        try {
            switch (state) {
                case "invCompleted": {
                    Response forwardingResponse = (Response) responseEvent.getResponse().clone();
                    forwardingResponse = rewriteResponseHeader(forwardingResponse);
                    switch (responseEvent.getResponse().getStatusCode()) {
                        case 180: {
                            logger.info("sending ringing to caller");
                            callerReg.sendResponse(sipServer.getSmFactory().createResponse(Response.RINGING, sessionInviteRequest), sessionInviteServerTransaction);

                            break;
                        }
                        case 200: {
                            logger.info("prijal som 200 ok from callee a resendujem na callera");
                            sessionOkResponse = forwardingResponse;
                            callerReg.sendResponse(forwardingResponse, sessionInviteServerTransaction);
                            // state = "invCompleted";
                            break;
                        }
                    }
                }
            }
        } catch (NullPointerException ex) {
            ex.printStackTrace();
        } catch (ParseException ex) {
            Logger.getLogger(CallSession.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void returnCallerTrying(Request request) {
        try {
            Response response = sipServer.getSmFactory().createResponse(Response.TRYING, request);
            logger.info("sending trying " + response.toString());
            callerReg.sendResponse(response, sessionInviteServerTransaction);
        } catch (ParseException ex) {
            Logger.getLogger(CallSession.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void forwardRequest(Request request) {
        try {
            request.removeHeader(ViaHeader.NAME);
            ViaHeader viaHeader = sipServer.getShFactory().createViaHeader(sipServer.getSipDomain(), sipServer.getSipPort(), sipServer.getSipTransport(), sessionBranch);
            ToHeader toHeader = (ToHeader) request.getHeader(ToHeader.NAME);

            request.addFirst(viaHeader);

            SipURI requsetUri = (SipURI) request.getRequestURI();
            logger.info("calleereg host " + calleeReg.getRegHost());
            requsetUri.setPort(calleeReg.getRegPort());
            requsetUri.setHost(calleeReg.getRegHost());

            SipURI thSipUri = (SipURI) toHeader.getAddress().getURI();
            thSipUri.setUser(calleeReg.getDev().getName());
            thSipUri.setPort(sipServer.getSipPort());
            toHeader.getAddress().setDisplayName(calleeReg.getDev().getName());
            logger.info("SIPURI " + requsetUri.toString());
            ClientTransaction ct = sipServer.getSipProvider().getNewClientTransaction(request);
            sessionInviteClientTransaction = ct;
            logger.info("FORWARD OUT ->>>>>>>>>>>>>>>>>>" + request.toString());
            //ct.getdi
            ct.sendRequest();

        } catch (NullPointerException ex) {
            ex.printStackTrace();
        } catch (ParseException | InvalidArgumentException | SipException ex) {
            Logger.getLogger(CallSession.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    public Response rewriteResponseHeader(Response forwardingResponse) {
        try {
            forwardingResponse.removeHeader(ViaHeader.NAME);
            //   ViaHeader vh = (ViaHeader) sipServer.getShFactory().createViaHeader(sipServer.getSipDomain(), sipServer.getSipPort(), sipServer.getSipTransport(),sessionBranch);
            ViaHeader vh = (ViaHeader) sessionInviteRequest.getHeader(ViaHeader.NAME);
            forwardingResponse.addFirst(vh);
            logger.info("vh " + vh.toString());

          //  forwardingResponse.addFirst();
            SipUri sipUri = (SipUri) sipServer.getSaFactory().createSipURI(callerReg.getDev().getExtension().toString(), sipServer.getSipDomain());
            sipUri.setTransportParam(sipServer.getSipTransport());
            sipUri.setPort(sipServer.getSipPort());
            logger.info("sipUri " + sipUri.toString());
            Address address = sipServer.getSaFactory().createAddress(callerReg.getDev().getExtension().toString(), sipUri);
            address.setDisplayName(callerReg.getDev().getName());
            logger.info("address " + address.toString());
            ContactHeader contactHeader = sipServer.getShFactory().createContactHeader(address);
            forwardingResponse.setHeader(contactHeader);

        } catch (NullPointerException np) {
            np.printStackTrace();
        } catch (ParseException | SipException ex) {
            Logger.getLogger(CallSession.class.getName()).log(Level.SEVERE, null, ex);
        }
        return forwardingResponse;
    }

    public Request rewriteRequestHeader(Request forwardingRequest) {

        try {
            //contact;
            if (findExtension(callerReg) == null) {
                logger.error("not found extension");
            } else {
                FromHeader fh = (FromHeader) forwardingRequest.getHeader(FromHeader.NAME);
                SipUri fhSipUri = (SipUri) fh.getAddress().getURI();
                fhSipUri.setHost(sipServer.getSipDomain());
                fhSipUri.setUser(callerReg.getDev().getExtension().toString());
                fhSipUri.setPort(sipServer.getSipPort());
                fh.getAddress().setDisplayName(callerReg.getDev().getName());

                SipURI sipUri = sipServer.getSaFactory().createSipURI(calleeReg.getDev().getExtension().toString(), sipServer.getSipDomain());
                sipUri.setTransportParam(sipServer.getSipTransport());
                sipUri.setPort(sipServer.getSipPort());
                logger.info("sipUri " + sipUri.toString());
                Address address = sipServer.getSaFactory().createAddress(calleeReg.getDev().getExtension().toString(), sipUri);
                address.setDisplayName(calleeReg.getDev().getName());
                logger.info("address " + address.toString());
                ContactHeader contactHeader = sipServer.getShFactory().createContactHeader(address);
                forwardingRequest.setHeader(contactHeader);

                // SipURI fromSipUri = (SipURI) sipServer.getSaFactory().createSipURI(callerReg.getDev().getName(), callerReg.getDev().getHost());
                // fromSipUri.setHost(sipServer.getSipDomain());
                // fromSipUri.setUser(callerReg.getDev().getExtension().toString());
            }

        } catch (ParseException ex) {
            Logger.getLogger(CallSession.class.getName()).log(Level.SEVERE, null, ex);
        } catch (NullPointerException np) {
            np.printStackTrace();
        }
        return forwardingRequest;
    }

    public Integer findExtension(Registration reg) {
        for (Registration registr : sipServer.getRegistrationList()) {
            if (registr.equals(reg)) {
                return registr.getDev().getExtension();
            }

        }
        return null;
    }

    public Server getSipServer() {
        return sipServer;
    }

    public ServerTransaction getSessionInviteServerTransaction() {
        return sessionInviteServerTransaction;
    }

    public String getSessionBranch() {
        return sessionBranch;
    }

    public Registration getCallerReg() {
        return callerReg;
    }

    public Registration getCalleeReg() {
        return calleeReg;
    }

    public CallIdHeader getCallIdHeader() {
        return callIdHeader;
    }

    public Request getSessionInviteRequest() {
        return sessionInviteRequest;
    }

}

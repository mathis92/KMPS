/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package sk.mathis.stuba.sipproxy.equip;

import gov.nist.javax.sip.address.SipUri;
import java.text.ParseException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sip.ClientTransaction;
import javax.sip.InvalidArgumentException;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipException;
import javax.sip.TransactionAlreadyExistsException;
import javax.sip.TransactionUnavailableException;
import javax.sip.address.Address;
import javax.sip.address.SipURI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.ToHeader;
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
    private RequestEvent sessionInviteRequest;
    private RequestEvent sessionACKrequest;
    private ServerTransaction sessionInviteServerTransaction;
    private ClientTransaction sessionInviteClientTransaction;
    private ClientTransaction sessionOKClientTransaction;
    private ClientTransaction sessionACKClientTransaction;
    private ClientTransaction sessionBYEClientTransaction;
    private Response sessionOkResponse;
    private String sessionBranch;
    private String sessionByeBranch;
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
            logger.info("CALLSESSION state " + state);
            switch (state) {
                case "invReceived": {
                    logger.info("FORWARDING IN ->>>>>>>>>>>>>> " + requestEvent.getRequest().toString());
                    callIdHeader = (CallIdHeader) requestEvent.getRequest().getHeader(CallIdHeader.NAME);
                    logger.info("callidHeader first invite " + callIdHeader);
                    sessionInviteServerTransaction = this.sipServer.getST(requestEvent);
                    sessionInviteRequest = requestEvent;

                    Request forwardingRequest = (Request) requestEvent.getRequest().clone();
                    logger.info("vytvoril som forwarding request");

                    forwardingRequest = rewriteRequestHeader(forwardingRequest, callerReg, calleeReg);
                    logger.info("REWRITED REQUEST " + forwardingRequest.toString());
                    sessionBranch = sipServer.createBranch();

                    forwardRequest(forwardingRequest, calleeReg);
                    returnCallerTrying(requestEvent.getRequest());

                    state = "invCompleted";
                    break;
                }
                case "connected": {
                    if (requestEvent.getRequest().getMethod().equals(Request.ACK)) {
                        logger.info("going to resend ACK");
                        sessionACKrequest = requestEvent;
                        resendAck();
                    } else if (requestEvent.getRequest().getMethod().equals(Request.BYE)) {
                        logger.info("going to process BYE");
                        Request newByeRequest = (Request) requestEvent.getRequest().clone();
                        Response okResponse = sipServer.getSmFactory().createResponse(Response.OK, requestEvent.getRequest());
                        logger.info("ok response created " + okResponse.toString());

                        ServerTransaction st = requestEvent.getServerTransaction();
                        if (st == null) {
                            st = sipServer.getSipProvider().getNewServerTransaction(requestEvent.getRequest());
                            logger.info("server transaction for bye to caller " + st.toString());
                            st.sendResponse(okResponse);
                            logger.info("ok response sent");
                        } else {
                            logger.info("server transaction for bye to caller " + st.toString());
                            st.sendResponse(okResponse);
                            logger.info("ok response sent");
                        }

                        if (solveActionSide(requestEvent)) {
                            rewriteRequestHeader(newByeRequest, callerReg, calleeReg);
                            forwardBye(newByeRequest);
                        } else {
                            rewriteRequestHeader(newByeRequest, calleeReg, callerReg);
                            forwardByeCallee(newByeRequest);

                        }

                        state = "done";
                    }

                    break;
                }
                case "invCompleted": {

                    if (requestEvent.getRequest().getMethod().equals(Request.CANCEL)) {
                        logger.info("zachyteny CANCEL " + requestEvent.getRequest().toString());

                        Response okResponse = sipServer.getSmFactory().createResponse(Response.OK, requestEvent.getRequest());
                        logger.info("ok response created posielam callerovi " + okResponse.toString());
                        ServerTransaction st = requestEvent.getServerTransaction();
                        if (st == null) {
                            st = sipServer.getSipProvider().getNewServerTransaction(requestEvent.getRequest());
                            logger.info("server transaction for bye to caller " + st.toString());
                            st.sendResponse(okResponse);
                            logger.info("ok response sent");
                        } else {
                            logger.info("server transaction for bye to caller " + st.toString());
                            st.sendResponse(okResponse);
                            logger.info("ok response sent");
                        }
                        Request newCancelRequest = (Request) requestEvent.getRequest().clone();
                        state = "cancelled";

                        rewriteRequestHeader(newCancelRequest, callerReg, calleeReg);
                        forwardRequest(newCancelRequest, calleeReg);

                    }
                    if (requestEvent.getRequest().getMethod().equals(Request.INVITE)) {
                        logger.info("zachyteny INVITE pre busy here " + requestEvent.getRequest().toString());
                    }
                    break;
                }
                case "done": {
                    break;
                }
            }
        } catch (NullPointerException ex) {
            ex.printStackTrace();
        } catch (ParseException | InvalidArgumentException ex) {
            Logger.getLogger(CallSession.class.getName()).log(Level.SEVERE, null, ex);
        } catch (TransactionAlreadyExistsException ex) {
            Logger.getLogger(CallSession.class.getName()).log(Level.SEVERE, null, ex);
        } catch (TransactionUnavailableException ex) {
            Logger.getLogger(CallSession.class.getName()).log(Level.SEVERE, null, ex);
        } catch (SipException ex) {
            Logger.getLogger(CallSession.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public boolean solveActionSide(RequestEvent requestEvent) {
        FromHeader fh = (FromHeader) requestEvent.getRequest().getHeader(FromHeader.NAME);
        String userExt = ((SipURI) fh.getAddress().getURI()).getUser();
        String callerExt = callerReg.getDev().getExtension().toString();
        logger.info(userExt + " -> " + callerExt);
        if (callerExt.equals(userExt)) {
            logger.info("bye from caller");
            return true;
        } else {
            logger.info("bye from callee");
            return false;
        }

    }

    public void forwardByeCallee(Request request) {
        try {
            logger.info("in processBYE request " + request.toString());
            ViaHeader vh = (ViaHeader) request.getHeader(ViaHeader.NAME);
            sessionByeBranch = sipServer.createBranch();
            request.removeHeader(ViaHeader.NAME);
            ViaHeader viaHeader = sipServer.getShFactory().createViaHeader(sipServer.getSipDomain(), sipServer.getSipPort(), sipServer.getSipTransport(), sessionByeBranch);
            ToHeader toHeader = (ToHeader) request.getHeader(ToHeader.NAME);
            request.addFirst(viaHeader);

            SipURI requsetUri = (SipURI) request.getRequestURI();
            logger.info("calleereg host " + callerReg.getRegHost());
            requsetUri.setPort(callerReg.getRegPort());
            requsetUri.setHost(callerReg.getRegHost());

            SipURI thSipUri = (SipURI) toHeader.getAddress().getURI();
            thSipUri.setUser(callerReg.getDev().getName());
            thSipUri.setPort(sipServer.getSipPort());
            toHeader.getAddress().setDisplayName(callerReg.getDev().getName());
            logger.info("SIPURI " + requsetUri.toString());
            ClientTransaction ct = sipServer.getSipProvider().getNewClientTransaction(request);
            sessionBYEClientTransaction = ct;
            logger.info("FORWARD OUT BYE ->>>>>>>>>>>>>>>>>>" + request.toString());
            sessionACKrequest.getDialog().sendRequest(ct);

        } catch (NullPointerException np) {
            np.printStackTrace();
        } catch (ParseException ex) {
            Logger.getLogger(CallSession.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvalidArgumentException ex) {
            Logger.getLogger(CallSession.class.getName()).log(Level.SEVERE, null, ex);
        } catch (SipException ex) {
            Logger.getLogger(CallSession.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void forwardBye(Request request) {
        try {
            logger.info("in processBYE request " + request.toString());
            ViaHeader vh = (ViaHeader) request.getHeader(ViaHeader.NAME);
            sessionByeBranch = sipServer.createBranch();
            request.removeHeader(ViaHeader.NAME);
            ViaHeader viaHeader = sipServer.getShFactory().createViaHeader(sipServer.getSipDomain(), sipServer.getSipPort(), sipServer.getSipTransport(), sessionByeBranch);
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
            sessionBYEClientTransaction = ct;
            logger.info("FORWARD OUT BYE ->>>>>>>>>>>>>>>>>>" + request.toString());
            sessionOKClientTransaction.getDialog().sendRequest(ct);

        } catch (NullPointerException np) {
            np.printStackTrace();
        } catch (ParseException ex) {
            Logger.getLogger(CallSession.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvalidArgumentException ex) {
            Logger.getLogger(CallSession.class.getName()).log(Level.SEVERE, null, ex);
        } catch (SipException ex) {
            Logger.getLogger(CallSession.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void resendAck() {
        try {
            Request ack = sessionInviteClientTransaction.getDialog().createAck(((CSeqHeader) sessionOkResponse.getHeader(CSeqHeader.NAME)).getSeqNumber());
            logger.info("generated ack " + ack.toString());
            sessionInviteClientTransaction.getDialog().sendAck(ack);
        } catch (InvalidArgumentException | SipException ex) {
            Logger.getLogger(CallSession.class.getName()).log(Level.SEVERE, null, ex);
        } catch (NullPointerException np) {
            np.printStackTrace();
        }
    }

    public void responseReceived(ResponseEvent responseEvent) {
        try {
            logger.info("RECEIVED RESPONSE " + responseEvent.getResponse().toString());
            Response forwardingResponse = (Response) responseEvent.getResponse().clone();
            forwardingResponse = rewriteResponseHeader(forwardingResponse, callerReg);
            switch (state) {
                case "invCompleted": {
                    Integer statusCode = null;
                    if (responseEvent.getResponse().getStatusCode() >= 400) {
                        statusCode = 400;
                    } else {
                        statusCode = responseEvent.getResponse().getStatusCode();
                    }
                    switch (statusCode) {
                        case 180: {
                            logger.info("sending ringing to caller");
                            callerReg.sendResponse(sipServer.getSmFactory().createResponse(Response.RINGING, sessionInviteRequest.getRequest()), sessionInviteServerTransaction);

                            break;
                        }
                        case 200: {
                            logger.info("prijal som 200 ok from callee a resendujem na callera");
                            sessionOkResponse = forwardingResponse;
                            sessionOKClientTransaction = responseEvent.getClientTransaction();
                            callerReg.sendResponse(forwardingResponse, sessionInviteServerTransaction);

                            state = "connected";
                            break;
                        }
                        case (400): {
                            logger.info("prijal som 487 request Terminated preposielam na callera " + responseEvent.getResponse().getReasonPhrase());
                            sessionInviteServerTransaction.sendResponse(forwardingResponse);
                            state = "cancelled";
                            break;
                        }
                    }
                    break;
                }
                case "cancelled": {

                    switch (responseEvent.getResponse().getStatusCode()) {
                        case 200: {
                            logger.info("prijal som 200 ok po cancelli");
                            state = "END";
                            break;
                        }

                    }
                    break;
                }
                case "done": {
                    switch (responseEvent.getResponse().getStatusCode()) {
                        case 200: {
                            logger.info("prijal som 200 ok po odoslani baju");
                            state = "END";
                            break;
                        }
                    }
                    break;
                }
            }
        } catch (NullPointerException ex) {
            ex.printStackTrace();
        } catch (ParseException ex) {
            Logger.getLogger(CallSession.class.getName()).log(Level.SEVERE, null, ex);
        } catch (SipException ex) {
            Logger.getLogger(CallSession.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvalidArgumentException ex) {
            Logger.getLogger(CallSession.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void returnCallerTrying(Request request) {
        try {
            Response response = sipServer.getSmFactory().createResponse(Response.TRYING, request);
            logger.info("sending trying " + response.toString());
            callerReg.sendResponse(response, sessionInviteServerTransaction);

        } catch (ParseException ex) {
            Logger.getLogger(CallSession.class
                    .getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void forwardRequest(Request request, Registration call) {
        try {
            request.removeHeader(ViaHeader.NAME);
            ViaHeader viaHeader = sipServer.getShFactory().createViaHeader(sipServer.getSipDomain(), sipServer.getSipPort(), sipServer.getSipTransport(), sessionBranch);
            ToHeader toHeader = (ToHeader) request.getHeader(ToHeader.NAME);

            request.addFirst(viaHeader);

            SipURI requsetUri = (SipURI) request.getRequestURI();
            logger.info("calleereg host " + call.getRegHost());
            requsetUri.setPort(call.getRegPort());
            requsetUri.setHost(call.getRegHost());

            SipURI thSipUri = (SipURI) toHeader.getAddress().getURI();
            thSipUri.setUser(call.getDev().getName());
            thSipUri.setPort(sipServer.getSipPort());
            toHeader.getAddress().setDisplayName(call.getDev().getName());
            logger.info("SIPURI " + requsetUri.toString());
            ClientTransaction ct = sipServer.getSipProvider().getNewClientTransaction(request);
            sessionInviteClientTransaction = ct;
            logger.info("FORWARD OUT ->>>>>>>>>>>>>>>>>>" + request.toString());
            //ct.getdi
            ct.sendRequest();

        } catch (NullPointerException ex) {
            ex.printStackTrace();

        } catch (ParseException | InvalidArgumentException | SipException ex) {
            Logger.getLogger(CallSession.class
                    .getName()).log(Level.SEVERE, null, ex);
        }

    }

    public void forwardRequestCallee(Request request) {
        try {
            request.removeHeader(ViaHeader.NAME);
            ViaHeader viaHeader = sipServer.getShFactory().createViaHeader(sipServer.getSipDomain(), sipServer.getSipPort(), sipServer.getSipTransport(), sessionBranch);
            ToHeader toHeader = (ToHeader) request.getHeader(ToHeader.NAME);

            request.addFirst(viaHeader);

            SipURI requsetUri = (SipURI) request.getRequestURI();
            logger.info("calleereg host " + calleeReg.getRegHost());
            requsetUri.setPort(callerReg.getRegPort());
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
            Logger.getLogger(CallSession.class
                    .getName()).log(Level.SEVERE, null, ex);
        }

    }

    public Response rewriteResponseHeader(Response forwardingResponse, Registration caller) {
        try {
            forwardingResponse.removeHeader(ViaHeader.NAME);
            //   ViaHeader vh = (ViaHeader) sipServer.getShFactory().createViaHeader(sipServer.getSipDomain(), sipServer.getSipPort(), sipServer.getSipTransport(),sessionBranch);
            ViaHeader vh = (ViaHeader) sessionInviteRequest.getRequest().getHeader(ViaHeader.NAME);
            forwardingResponse.addFirst(vh);
            logger.info("vh " + vh.toString());

            //  forwardingResponse.addFirst();
            SipUri sipUri = (SipUri) sipServer.getSaFactory().createSipURI(caller.getDev().getExtension().toString(), sipServer.getSipDomain());
            sipUri.setTransportParam(sipServer.getSipTransport());
            sipUri.setPort(sipServer.getSipPort());
            logger.info("sipUri " + sipUri.toString());
            Address address = sipServer.getSaFactory().createAddress(caller.getDev().getExtension().toString(), sipUri);
            address.setDisplayName(caller.getDev().getName());
            logger.info("address " + address.toString());
            ContactHeader contactHeader = sipServer.getShFactory().createContactHeader(address);
            forwardingResponse.setHeader(contactHeader);

        } catch (NullPointerException np) {
            np.printStackTrace();

        } catch (ParseException | SipException ex) {
            Logger.getLogger(CallSession.class
                    .getName()).log(Level.SEVERE, null, ex);
        }
        return forwardingResponse;
    }

    public Request rewriteRequestHeader(Request forwardingRequest, Registration caller, Registration callee) {

        try {
            //contact;
            if (findExtension(caller) == null) {
                logger.error("not found extension");
            } else {
                FromHeader fh = (FromHeader) forwardingRequest.getHeader(FromHeader.NAME);
                SipUri fhSipUri = (SipUri) fh.getAddress().getURI();
                fhSipUri.setHost(sipServer.getSipDomain());
                fhSipUri.setUser(caller.getDev().getExtension().toString());
                fhSipUri.setPort(sipServer.getSipPort());
                fh.getAddress().setDisplayName(caller.getDev().getName());

                SipURI sipUri = sipServer.getSaFactory().createSipURI(caller.getDev().getExtension().toString(), sipServer.getSipDomain());
                sipUri.setTransportParam(sipServer.getSipTransport());
                sipUri.setPort(sipServer.getSipPort());
                logger.info("sipUri " + sipUri.toString());
                Address address = sipServer.getSaFactory().createAddress(callee.getDev().getExtension().toString(), sipUri);
                address.setDisplayName(callee.getDev().getName());
                logger.info("address " + address.toString());
                ContactHeader contactHeader = sipServer.getShFactory().createContactHeader(address);
                forwardingRequest.setHeader(contactHeader);

                // SipURI fromSipUri = (SipURI) sipServer.getSaFactory().createSipURI(callerReg.getDev().getName(), callerReg.getDev().getHost());
                // fromSipUri.setHost(sipServer.getSipDomain());
                // fromSipUri.setUser(callerReg.getDev().getExtension().toString());
            }

        } catch (ParseException ex) {
            Logger.getLogger(CallSession.class
                    .getName()).log(Level.SEVERE, null, ex);
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

    public RequestEvent getSessionInviteRequest() {
        return sessionInviteRequest;
    }

    public String getState() {
        return state;
    }

}

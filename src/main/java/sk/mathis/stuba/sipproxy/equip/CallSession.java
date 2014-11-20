/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package sk.mathis.stuba.sipproxy.equip;

import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.header.MaxForwards;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
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
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.TooManyHopsException;
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
    private ClientTransaction session400ClientTransaction;
    private ClientTransaction sessionACKClientTransaction;
    private ClientTransaction sessionBYEClientTransaction;
    private Queue<String> callSessionMessagesBuffer;
    private Queue<Object> callTransactionBuffer;
    private Response sessionOkResponse;
    private String sessionBranch;
    private String sessionByeBranch;
    private final org.slf4j.Logger logger;
    private Date startTime = null;
    private Date endTime = null;
    private String callDuration;

    public CallSession(Registration callerReg, Registration calleeReg, Server sipServer, CallIdHeader callIdHeader) {
        this.callSessionMessagesBuffer = new ConcurrentLinkedDeque();
        this.callTransactionBuffer = new ConcurrentLinkedDeque<Object>();
        this.logger = LoggerFactory.getLogger(CallSession.class);
        this.sipServer = sipServer;
        this.calleeReg = calleeReg;
        //    logger.info("callee reg " + calleeReg.getDev().getHost() + " " + calleeReg.getDev().getExtension());
        this.callerReg = callerReg;
        //     logger.info("caller reg " + callerReg.getDev().getHost() + " " + callerReg.getDev().getExtension());
        this.callIdHeader = callIdHeader;
    }

    public void requestReceived(RequestEvent requestEvent) {
        callSessionMessagesBuffer.add("REQUEST RECEIVED \n" + requestEvent.getRequest().toString());
        callTransactionBuffer.add(requestEvent.getRequest());
        try {
            logger.debug("CALLSESSION state " + state);
            switch (state) {
                case "invReceived": {

                    if (calleeReg == null) {
                        sipServer.getST(requestEvent).sendResponse(sipServer.getSmFactory().createResponse(Response.NOT_FOUND, requestEvent.getRequest()));
                        callSessionMessagesBuffer.add("RESPONSE SENT \n" + (sipServer.getSmFactory().createResponse(Response.NOT_FOUND, requestEvent.getRequest()).toString()));
                        callTransactionBuffer.add(sipServer.getSmFactory().createResponse(Response.NOT_FOUND, requestEvent.getRequest()));
                        logger.debug("nenasiel som CALLEEHO");
                        break;
                    }
                    if (callerReg == null) {
                        sipServer.getST(requestEvent).sendResponse(sipServer.getSmFactory().createResponse(Response.NOT_FOUND, requestEvent.getRequest()));
                        callSessionMessagesBuffer.add("RESPONSE SENT \n" + (sipServer.getSmFactory().createResponse(Response.NOT_FOUND, requestEvent.getRequest()).toString()));
                        callTransactionBuffer.add(sipServer.getSmFactory().createResponse(Response.NOT_FOUND, requestEvent.getRequest()));
                        logger.debug("nenasiel som CALLERA");
                        break;
                    }

                    //           logger.info("RECEIVED ->>>>>>>>>>>>>> " + requestEvent.getRequest().toString());
                    callIdHeader = (CallIdHeader) requestEvent.getRequest().getHeader(CallIdHeader.NAME);
                    //              logger.info("callidHeader first invite " + callIdHeader);
                    sessionInviteServerTransaction = this.sipServer.getST(requestEvent);
                    sessionInviteRequest = requestEvent;

                    Request forwardingRequest = (Request) requestEvent.getRequest().clone();
                    //        logger.info("vytvoril som forwarding request");

                    forwardingRequest = rewriteRequestHeader(forwardingRequest, callerReg, calleeReg);
                    //        logger.info("REWRITED REQUEST " + forwardingRequest.toString());
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
                        startTime = new Date();
                        resendAck();
                    } else if (requestEvent.getRequest().getMethod().equals(Request.BYE)) {
                        endTime = new Date();
                        //       computeDuration();
                        logger.info("going to process BYE");
                        Request newByeRequest = (Request) requestEvent.getRequest().clone();
                        Response okResponse = sipServer.getSmFactory().createResponse(Response.OK, requestEvent.getRequest());
                        logger.info("ok response created " + okResponse.toString());

                        ServerTransaction st = requestEvent.getServerTransaction();
                        if (st == null) {
                            st = sipServer.getSipProvider().getNewServerTransaction(requestEvent.getRequest());
                            logger.info("server transaction for bye to caller " + st.toString());
                            st.sendResponse(okResponse);
                             callTransactionBuffer.add(okResponse);
                            callSessionMessagesBuffer.add("RESPONSE SENT \n" + okResponse.toString());
                            logger.info("ok response sent");
                        } else {
                            logger.info("server transaction for bye to caller " + st.toString());
                            st.sendResponse(okResponse);
                             callTransactionBuffer.add(okResponse);
                            callSessionMessagesBuffer.add("RESPONSE SENT \n" + okResponse.toString());
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
                        //                     logger.info("zachyteny CANCEL " + requestEvent.getRequest().toString());

                        Response okResponse = sipServer.getSmFactory().createResponse(Response.OK, requestEvent.getRequest());
                        logger.info("ok response created posielam callerovi \n" + okResponse.toString());
                        ServerTransaction st = requestEvent.getServerTransaction();
                        if (st == null) {
                            st = sipServer.getSipProvider().getNewServerTransaction(requestEvent.getRequest());
                            logger.info("server transaction for bye to caller " + st.toString());
                            st.sendResponse(okResponse);
                             callTransactionBuffer.add(okResponse);
                            callSessionMessagesBuffer.add("RESPONSE SENT \n" + okResponse.toString());
                            logger.info("ok response sent");
                        } else {
                            logger.info("server transaction for bye to caller " + st.toString());
                            st.sendResponse(okResponse);
                             callTransactionBuffer.add(okResponse);
                            callSessionMessagesBuffer.add("RESPONSE SENT \n" + okResponse.toString());
                            logger.info("ok response sent");
                        }
                        Request newCancelRequest = (Request) requestEvent.getRequest().clone();
                        state = "cancelled";

                        rewriteRequestHeader(newCancelRequest, callerReg, calleeReg);
                        forwardRequest(newCancelRequest, calleeReg);

                    }
                    if (requestEvent.getRequest().getMethod().equals(Request.INVITE)) {
                        logger.info("zachyteny INVITE pre busy here \n" + requestEvent.getRequest().toString());
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
        String callerName = callerReg.getDev().getName();
        logger.info(userExt + " -> " + callerExt + "|" + callerName);
        if (callerExt.equals(userExt) || callerName.equals(userExt)) {
            logger.info("bye from caller");
            return true;
        } else {
            logger.info("bye from callee");
            return false;
        }

    }

    public void updateMaxForwardsHeader(Request request) {
        try {

            MaxForwardsHeader mf = (MaxForwardsHeader) request.getHeader(MaxForwardsHeader.NAME);
            if (mf != null) {
                mf.decrementMaxForwards();
            } else {
                mf = sipServer.getShFactory().createMaxForwardsHeader(70);
                request.addHeader(mf);
            }
            logger.info("Max forward Header decremented " + mf.getMaxForwards());
        } catch (TooManyHopsException ex) {
            Logger.getLogger(CallSession.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvalidArgumentException ex) {
            Logger.getLogger(CallSession.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void forwardByeCallee(Request request) {
        try {
            logger.info("in processBYE request " + request.toString());
            //ViaHeader vh = (ViaHeader) request.getHeader(ViaHeader.NAME);
            sessionByeBranch = sipServer.createBranch();
            // request.removeHeader(ViaHeader.NAME);
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
            this.updateMaxForwardsHeader(request);
            logger.info("REWRITTEN REQUEST  ->> OUT \n" + request.toString());

            sessionACKrequest.getDialog().sendRequest(ct);
            callTransactionBuffer.add(request);
            callSessionMessagesBuffer.add("REQUEST SENT \n" + request.toString());

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
            // request.removeHeader(ViaHeader.NAME);
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
            this.updateMaxForwardsHeader(request);
            logger.info("REWRITTEN REQUEST  ->> OUT \n" + request.toString());

            sessionOKClientTransaction.getDialog().sendRequest(ct);
            callTransactionBuffer.add(request);
            callSessionMessagesBuffer.add("REQUEST SENT \n" + request.toString());

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
            Request ack = sessionOKClientTransaction.getDialog().createAck(((CSeqHeader) sessionOkResponse.getHeader(CSeqHeader.NAME)).getSeqNumber());
            logger.info("generated ack \n" + ack.toString());
            sessionOKClientTransaction.getDialog().sendAck(ack);
            callTransactionBuffer.add(ack);
            callSessionMessagesBuffer.add("REQUEST SENT \n" + ack.toString());
        } catch (InvalidArgumentException | SipException ex) {
            Logger.getLogger(CallSession.class.getName()).log(Level.SEVERE, null, ex);
        } catch (NullPointerException np) {
            np.printStackTrace();
        }
    }

    public void responseReceived(ResponseEvent responseEvent) {
        callSessionMessagesBuffer.add("RESPONSE RECEIVED \n" + responseEvent.getResponse().toString());
        callTransactionBuffer.add(responseEvent.getResponse());
        try {
            logger.info("RECEIVED RESPONSE \n" + responseEvent.getResponse().toString());
            Response forwardingResponse = (Response) responseEvent.getResponse().clone();
            forwardingResponse = rewriteResponseHeader(forwardingResponse, callerReg);
            logger.debug("RESPONSE SESSION STATE " + state);
            logger.debug("stat code " + responseEvent.getResponse().getStatusCode());
            Integer statusCode;
            if (responseEvent.getResponse().getStatusCode() >= 400) {
                statusCode = 400;
            } else {
                statusCode = responseEvent.getResponse().getStatusCode();
            }
            logger.debug("status code" + statusCode);
            switch (state) {
                case "invCompleted": {

                    switch (statusCode) {
                        case 180: {
                            logger.info("sending ringing to caller");
                            callerReg.sendResponse(sipServer.getSmFactory().createResponse(Response.RINGING, sessionInviteRequest.getRequest()), sessionInviteServerTransaction);
                           callTransactionBuffer.add(sipServer.getSmFactory().createResponse(Response.RINGING, sessionInviteRequest.getRequest()));
                            callSessionMessagesBuffer.add("RESPONSE SENT \n" + (sipServer.getSmFactory().createResponse(Response.RINGING, sessionInviteRequest.getRequest()).toString()));
                            break;
                        }
                        case 200: {
                            logger.info("prijal som 200 ok from callee a resendujem na callera");
                            sessionOkResponse = forwardingResponse;
                            sessionOKClientTransaction = responseEvent.getClientTransaction();
                            callerReg.sendResponse(forwardingResponse, sessionInviteServerTransaction);
                             callTransactionBuffer.add(forwardingResponse);
                            callSessionMessagesBuffer.add("RESPONSE SENT \n" + forwardingResponse.toString());
                            state = "connected";
                            break;
                        }
                        case (400): {
                            logger.info("prijal som 487 response Terminated preposielam na callera \n" + responseEvent.getResponse().getReasonPhrase());

                            sessionInviteServerTransaction.sendResponse(forwardingResponse);
                            callTransactionBuffer.add(forwardingResponse);
                            callSessionMessagesBuffer.add("RESPONSE SENT \n" + forwardingResponse.toString());
                            logger.info("odoslal som to tu");
                            // session400ClientTransaction = responseEvent.getClientTransaction();
                            // Request ack = session400ClientTransaction.getDialog().createAck(((CSeqHeader) responseEvent.getResponse().getHeader(CSeqHeader.NAME)).getSeqNumber());
                            // session400ClientTransaction.getDialog().sendAck(ack);
                            //  logger.info("ACK na 400ku odoslany");
                            state = "END";
                            break;
                        }
                    }
                    break;
                }
                case "cancelled": {

                    switch (statusCode) {
                        case 200: {
                            logger.info("prijal som 200 ok po cancelli");
                            //state = "END";
                            break;
                        }
                        case 400: {
                            logger.info("prijal som 487 response Terminated preposielam na callera \n" + responseEvent.getResponse().getReasonPhrase());

                            sessionInviteServerTransaction.sendResponse(forwardingResponse);
                            callTransactionBuffer.add(forwardingResponse);
                            callSessionMessagesBuffer.add("RESPONSE SENT \n" + forwardingResponse.toString());
                            logger.info("odoslal som to tu");
                            state = "END";
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
            callerReg.sendResponse(response, sessionInviteServerTransaction);
            callTransactionBuffer.add(response);
            callSessionMessagesBuffer.add("RESPONSE SENT \n" + response.toString());
        } catch (ParseException ex) {
            Logger.getLogger(CallSession.class
                    .getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void forwardRequest(Request request, Registration call) {
        try {
            //request.removeHeader(ViaHeader.NAME);
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
            //ct.getdi
            this.updateMaxForwardsHeader(request);
            logger.info("REWRITTEN REQUEST  ->> OUT \n" + request.toString());
            ct.sendRequest();
            callTransactionBuffer.add(request);
            callSessionMessagesBuffer.add("REQUEST SENT \n" + request.toString());

        } catch (NullPointerException ex) {
            ex.printStackTrace();

        } catch (ParseException | InvalidArgumentException | SipException ex) {
            Logger.getLogger(CallSession.class
                    .getName()).log(Level.SEVERE, null, ex);
        }

    }

    public void forwardRequestCallee(Request request) {
        try {
            // request.removeHeader(ViaHeader.NAME);
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
            //ct.getdi
            this.updateMaxForwardsHeader(request);
            logger.info("REWRITTEN REQUEST  ->> OUT \n" + request.toString());

            ct.sendRequest();
            callTransactionBuffer.add(request);
            callSessionMessagesBuffer.add("REQUEST SENT \n" + request.toString());

        } catch (NullPointerException ex) {
            ex.printStackTrace();

        } catch (ParseException | InvalidArgumentException | SipException ex) {
            Logger.getLogger(CallSession.class
                    .getName()).log(Level.SEVERE, null, ex);
        }

    }

    public Response rewriteResponseHeader(Response forwardingResponse, Registration caller) {
        logger.info("REWRITING RESPONSE HEADER");
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
                address.setDisplayName(caller.getDev().getName());
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

    public String computeDuration() {
        long duration;

        Date tmp;
        Date tmp1;

        if (endTime == null && startTime == null) {
            duration = 0;
        } else {
            if (endTime == null) {
                duration = new Date().getTime() - startTime.getTime();
            } else {
                duration = endTime.getTime() - startTime.getTime();

            }
        }
        SimpleDateFormat sdf = new SimpleDateFormat("mm:ss");
        Date dt = new Date(duration);
        callDuration = sdf.format(duration);
        //    logger.debug("DURATION " + callDuration);
        return callDuration;
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

    public String getCallDuration() {
        return callDuration;
    }

    public Date getStartTime() {
        return startTime;
    }

    public String getState() {
        return state;
    }

    public Queue<String> getCallSessionMessagesBuffer() {
        return callSessionMessagesBuffer;
    }

    public Queue<Object> getCallTransactionBuffer() {
        return callTransactionBuffer;
    }
}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package sk.mathis.stuba.sipproxy.equip;

import gov.nist.javax.sip.clientauthutils.DigestServerAuthenticationHelper;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sip.DialogTerminatedEvent;
import javax.sip.IOExceptionEvent;
import javax.sip.InvalidArgumentException;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipException;
import javax.sip.TimeoutEvent;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.header.ProxyAuthorizationHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;

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

        System.out.println(requestEvent.getRequest().toString());
        ViaHeader vheader = (ViaHeader) requestEvent.getRequest().getHeader("via");

        if (requestEvent.getRequest().getMethod().equals(Request.REGISTER)) {

            switch (dev.getState()) {
                case "regReceived": {
                    dev.setHost(vheader.getHost());
                    dev.setPort(vheader.getPort());

                    try {
                        Response authResponse = sipServer.getSmFactory().createResponse(Response.PROXY_AUTHENTICATION_REQUIRED, requestEvent.getRequest());
                        digestServerAuthHelper.generateChallenge(sipServer.getShFactory(), authResponse, sipServer.getSipDomain());
                        SipListener.this.sendResponse(requestEvent, authResponse);
                        System.out.println("OUTCOMING : " + authResponse.toString());
                    } catch (ParseException ex) {
                        Logger.getLogger(SipListener.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    dev.setState("authSent");

                    break;
                }
                case "authSent": {
                    ProxyAuthorizationHeader authorizedHeader = (ProxyAuthorizationHeader) requestEvent.getRequest().getHeader(ProxyAuthorizationHeader.NAME);
                    String requestedRealm = authorizedHeader.getRealm();
                    String requestedUsername = authorizedHeader.getUsername();
                    try {
                        try {
                            //System.out.println("hovno dve tri styri PET              " + createMd5(requestedUsername, requestedRealm, "heslo"));
                            
                            if (digestServerAuthHelper.doAuthenticateHashedPassword(requestEvent.getRequest(), createMd5(requestedUsername, requestedRealm, dev.getPasswd()))) {
                                dev.setState("authReceived");
                                {
                                    try {
                                        Response okResponse = sipServer.getSmFactory().createResponse(Response.OK, requestEvent.getRequest());
                                        SipListener.this.sendResponse(requestEvent, okResponse);
                                        System.out.println("OUTCOMING : " + okResponse.toString());
                                    } catch (ParseException ex) {
                                        Logger.getLogger(SipListener.class.getName()).log(Level.SEVERE, null, ex);
                                    }
                                }
                            }else{
                                System.out.println("nepodarilo sa overit meno a heslo");
                                dev.setInitialState();
                            }
                        } catch (NoSuchAlgorithmException ex) {
                            Logger.getLogger(SipListener.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    } catch (UnsupportedEncodingException ex) {
                        Logger.getLogger(SipListener.class.getName()).log(Level.SEVERE, null, ex);
                    }
                break;
           
                }
                case "authReceived": {
                    Response finalOkResponse = null;
                    try {
                        finalOkResponse = sipServer.getSmFactory().createResponse(Response.OK, requestEvent.getRequest());
                    } catch (ParseException ex) {
                        Logger.getLogger(SipListener.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    SipListener.this.sendResponse(requestEvent, finalOkResponse);
                    System.out.println("OUTCOMING : " + finalOkResponse.toString());
                }
                break;

            }
        }
    }

    public void sendResponse(RequestEvent requestEvent, Response resp) {
        ServerTransaction st = requestEvent.getServerTransaction();
        try {
            if (st == null) {
                st = this.sipServer.getsProvider().getNewServerTransaction(requestEvent.getRequest());
            }
            st.sendResponse(resp);

        } catch (SipException | InvalidArgumentException ex) {
            Logger.getLogger(SipListener.class
                    .getName()).log(Level.SEVERE, null, ex);
        }
    }

    public String createMd5(String requestedUsername, String requestedRealm, String dbPassword) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] hashBytes = md.digest((requestedUsername + ":" + requestedRealm + ":" + dbPassword).getBytes("UTF-8"));
        StringBuilder md5Hash = new StringBuilder();

        for (byte hByte : hashBytes) {
            md5Hash.append(String.format("%02x", hByte));
        }
        return md5Hash.toString();
    }

    @Override
    public void processResponse(ResponseEvent responseEvent) {
    }

    @Override
    public void processTimeout(TimeoutEvent timeoutEvent) {
    }

    @Override
    public void processIOException(IOExceptionEvent exceptionEvent) {
    }

    @Override
    public void processTransactionTerminated(TransactionTerminatedEvent transactionTerminatedEvent) {
    }

    @Override
    public void processDialogTerminated(DialogTerminatedEvent dialogTerminatedEvent) {
    }

}

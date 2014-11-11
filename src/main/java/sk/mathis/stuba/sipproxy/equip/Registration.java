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
import javax.sip.InvalidArgumentException;
import javax.sip.RequestEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipException;
import javax.sip.header.ProxyAuthorizationHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;

/**
 *
 * @author martinhudec
 */
public class Registration {

    private Server sipServer;
    private Users users;
    private UserDevice dev;
    private String state = "regReceived";
    private DigestServerAuthenticationHelper digestServerAuthHelper;

    public Registration(Server sipServer) {
        this.sipServer = sipServer;
    }

    public void register(RequestEvent requestEvent) {
        try {
            System.out.println(requestEvent.getRequest().toString());
            ViaHeader vheader = (ViaHeader) requestEvent.getRequest().getHeader("via");
            switch (this.state) {
                case "regReceived": {
                    dev.setHost(vheader.getHost());
                    dev.setPort(vheader.getPort());

                    try {
                        Response authResponse = sipServer.getSmFactory().createResponse(Response.PROXY_AUTHENTICATION_REQUIRED, requestEvent.getRequest());
                        digestServerAuthHelper.generateChallenge(sipServer.getShFactory(), authResponse, sipServer.getSipDomain());
                        this.sendResponse(requestEvent, authResponse);
                        System.out.println("OUTCOMING : " + authResponse.toString());
                    } catch (ParseException ex) {
                        Logger.getLogger(SipListener.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    state = ("authSent");

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
                                state = ("authReceived");
                                {
                                    try {
                                        Response okResponse = sipServer.getSmFactory().createResponse(Response.OK, requestEvent.getRequest());
                                        this.sendResponse(requestEvent, okResponse);
                                        System.out.println("OUTCOMING : " + okResponse.toString());
                                    } catch (ParseException ex) {
                                        Logger.getLogger(SipListener.class.getName()).log(Level.SEVERE, null, ex);
                                    }
                                }
                            } else {
                                System.out.println("nepodarilo sa overit meno a heslo");
                                //dev.setInitialState();
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
                        state = ("REGISTERED");
                    } catch (ParseException ex) {
                        Logger.getLogger(SipListener.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    this.sendResponse(requestEvent, finalOkResponse);
                    System.out.println("OUTCOMING : " + finalOkResponse.toString());
                }
                break;

            }

        } catch (NullPointerException ex) {
            System.out.println(ex.getMessage());
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

    public UserDevice getDevice(String host, Integer port) {
        for (UserDevice device : sipServer.getUsers().getUsersList()) {
            if (device.getHost().equals(host) && device.getPort().equals(port)) {
                return device;

            }
        }
        return null;
    }

    public UserDevice getDev() {
        return dev;
    }

}

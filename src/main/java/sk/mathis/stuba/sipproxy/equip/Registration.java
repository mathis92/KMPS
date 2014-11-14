/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package sk.mathis.stuba.sipproxy.equip;

import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.clientauthutils.DigestServerAuthenticationHelper;
import gov.nist.javax.sip.header.CallID;
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
import javax.sip.address.Address;
import javax.sip.address.SipURI;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.ProxyAuthorizationHeader;
import javax.sip.header.ToHeader;
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
    private String regHost;
    private Integer regPort;
    private String state = "regReceived";
    private ContactHeader contactHeader;
    private DigestServerAuthenticationHelper digestServerAuthHelper;
    private Address address;
    public Registration(Server sipServer) {
        this.sipServer = sipServer;
        try {
            digestServerAuthHelper = new DigestServerAuthenticationHelper();
        } catch (NoSuchAlgorithmException ex) {
            Logger.getLogger(SipListener.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void register(RequestEvent requestEvent) {
        System.out.println("aktualny stav registracie " + state);

        try {
            System.out.println("IN : ->>>>>>>>>>>>>>>> " + requestEvent.getRequest().toString());

            ViaHeader vheader = (ViaHeader) requestEvent.getRequest().getHeader("via");
            FromHeader fromHeader = (FromHeader) requestEvent.getRequest().getHeader(FromHeader.NAME);
            switch (state) {
                case "regReceived": {
                    regHost = vheader.getHost();
                    regPort = vheader.getPort();
                    address = fromHeader.getAddress();

                    try {
                        Response authResponse = sipServer.getSmFactory().createResponse(Response.PROXY_AUTHENTICATION_REQUIRED, requestEvent.getRequest());
                        digestServerAuthHelper.generateChallenge(sipServer.getShFactory(), authResponse, sipServer.getSipDomain());
                        this.sendResponse(requestEvent, authResponse);
                    } catch (ParseException ex) {
                        Logger.getLogger(SipListener.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    state = "authSent";

                    break;
                }
                case "authSent": {
                    ProxyAuthorizationHeader authorizedHeader = (ProxyAuthorizationHeader) requestEvent.getRequest().getHeader(ProxyAuthorizationHeader.NAME);

                    String requestedRealm = authorizedHeader.getRealm();
                    String requestedUsername = authorizedHeader.getUsername();

                    dev = getDevice(requestedUsername);

                    dev.setHost(vheader.getHost());
                    dev.setPort(vheader.getPort());

                    try {
                        try {
                            if (digestServerAuthHelper.doAuthenticateHashedPassword(requestEvent.getRequest(), createMd5(requestedUsername, requestedRealm, dev.getPasswd()))) {

                                {
                                    try {
                                        Response okResponse = sipServer.getSmFactory().createResponse(Response.OK, requestEvent.getRequest());

                                        state = "authReceived";

                                        SipURI sipUri = new SipUri();

                                        sipUri.setHost(this.sipServer.getSipDomain());
                                        sipUri.setUser(this.getDev().getExtension().toString());
                                        contactHeader = sipServer.getShFactory().createContactHeader(sipServer.getSaFactory().createAddress(this.getDev().getExtension().toString(), sipServer.getSaFactory().createSipURI(dev.getExtension().toString(), sipServer.getSipDomain())));
                                        okResponse.addHeader(contactHeader);
                                        this.sendResponse(requestEvent, okResponse);
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
                    } catch (ParseException ex) {
                        Logger.getLogger(SipListener.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    this.sendResponse(requestEvent, finalOkResponse);
                }
                break;

            }

        } catch (NullPointerException ex) {
            ex.printStackTrace();
        }

    }

    public void createCall(RequestEvent requestEvent) {
        try {
            if (!state.equals("authReceived")) {
                System.out.println("neukoncena registracia");
            } else {
                ToHeader toheader = (ToHeader) requestEvent.getRequest().getHeader(ToHeader.NAME);
                CallIdHeader callIdHeader = (CallIdHeader) requestEvent.getRequest().getHeader(CallIdHeader.NAME);

                CallSession session = null;
                System.out.println(address.toString() + " -> " + toheader.getAddress().toString());

                session = new CallSession(this, findCalleeRegistration(toheader.getAddress()));
                session.request(requestEvent);
            }

        } catch (NullPointerException ex) {
            ex.printStackTrace();
        }
    }

    public void sendResponse(RequestEvent requestEvent, Response resp) {
        ServerTransaction st = requestEvent.getServerTransaction();
        try {
            if (st == null) {
                st = this.sipServer.getsProvider().getNewServerTransaction(requestEvent.getRequest());
            }
            st.sendResponse(resp);
            System.out.println("OUT : ->>>>>>>>>>>>>> " + resp.toString());

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

    public UserDevice getDevice(String userName) {
        for (UserDevice device : sipServer.getUsers().getUsersList()) {
            if (device.getName().equals(userName)) {
                return device;

            }
        }
        return null;
    }

    public Registration findCalleeRegistration(Address address) {
        for (Registration reg : sipServer.getRegistrationList()) {
            if(reg.getAddress().equals(address)){
                return reg;
            }
        }

        return null;
    }

    public UserDevice getDev() {
        return dev;
    }

    public Integer getRegPort() {
        return regPort;
    }

    public Address getAddress() {
        return address;
    }

    public String getRegHost() {
        return regHost;
    }

}

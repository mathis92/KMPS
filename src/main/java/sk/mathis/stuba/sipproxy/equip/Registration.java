/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package sk.mathis.stuba.sipproxy.equip;

import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.clientauthutils.DigestServerAuthenticationHelper;
import gov.nist.javax.sip.header.Expires;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.logging.Level;
import javax.sip.InvalidArgumentException;
import javax.sip.RequestEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipException;
import javax.sip.address.Address;
import javax.sip.address.SipURI;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.ExpiresHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.ProxyAuthorizationHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author martinhudec
 */
public class Registration {

    private Server sipServer;
    private Users users;
    private UserDevice dev;
    private String regHost = null;
    private Integer regPort = null;
    private String state = "regReceived";
    private ArrayList<ContactHeader> contactHeaderList;
    private DigestServerAuthenticationHelper digestServerAuthHelper;
    // private Address address;
    private ArrayList<String> registrationMessages;
    private ServerTransaction st;
    private String requestedRealm;
    private String requestedUsername;

    private static final Logger logger = LoggerFactory.getLogger(Registration.class);

    public Registration(Server sipServer) {

        this.contactHeaderList = new ArrayList();
        this.registrationMessages = new ArrayList<>();
        this.sipServer = sipServer;
        try {
            digestServerAuthHelper = new DigestServerAuthenticationHelper();
        } catch (NoSuchAlgorithmException ex) {
            logger.error(ex.getLocalizedMessage());
// Logger.getLogger(SipListener.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void register(RequestEvent requestEvent) {
        logger.info("aktualny stav registracie " + state);
        
        try {
            logger.info("IN \n" + requestEvent.getRequest().toString());
            ViaHeader vheader = (ViaHeader) requestEvent.getRequest().getHeader("via");
            // FromHeader fromHeader = (FromHeader) requestEvent.getRequest().getHeader(FromHeader.NAME);
            switch (state) {
                case "regReceived": {
                    ContactHeader kungPAO = ((ContactHeader) requestEvent.getRequest().getHeader(ContactHeader.NAME));
                    if (kungPAO != null && kungPAO.getExpires() == 0) {
                        Response wifonkaResponse = sipServer.getSmFactory().createResponse(Response.INTERVAL_TOO_BRIEF, requestEvent.getRequest());
                        this.sendResponse(requestEvent, wifonkaResponse);
                        state = "UNREGISTERED";
                        dev.unregister();
                        logger.error("EXPIRES = 0 first REG -> zahadzujem registraciu");
                        regHost = null;
                        regPort = null;
                        break;
                    }
                    regHost = vheader.getHost();
                    regPort = vheader.getPort();
                    //        address = fromHeader.getAddress();
                    //      logger.info("first caught fromAddress " + address.toString());

                    try {
                        Response authResponse = sipServer.getSmFactory().createResponse(Response.PROXY_AUTHENTICATION_REQUIRED, requestEvent.getRequest());
                        digestServerAuthHelper.generateChallenge(sipServer.getShFactory(), authResponse, sipServer.getSipDomain());
                        this.sendResponse(requestEvent, authResponse);
                    } catch (ParseException ex) {
                        logger.error(ex.getLocalizedMessage());
//  Logger.getLogger(SipListener.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    state = "authSent";

                    break;
                }
                case "authSent": {
                    ProxyAuthorizationHeader authorizedHeader = (ProxyAuthorizationHeader) requestEvent.getRequest().getHeader(ProxyAuthorizationHeader.NAME);
                    // logger.info("som v stave authSent");
                    if (authorizedHeader == null) {
                        logger.error("AUTHORIZATION FAULT");
                        state = "regReceived";
                        break;
                    }

                    requestedRealm = authorizedHeader.getRealm();
                    requestedUsername = authorizedHeader.getUsername();

                    dev = getDevice(requestedUsername);
                    if (dev == null) {
                        Response notAuthResponse = sipServer.getSmFactory().createResponse(Response.UNAUTHORIZED, requestEvent.getRequest());
                        this.sendResponse(requestEvent, notAuthResponse);
                        break;
                    }

                    dev.setHost(vheader.getHost());
                    dev.setPort(vheader.getPort());

                    try {
                        try {
                            if (digestServerAuthHelper.doAuthenticateHashedPassword(requestEvent.getRequest(), createMd5(requestedUsername, requestedRealm, dev.getPasswd()))) {

                                {
                                    try {
                                        Response okResponse = sipServer.getSmFactory().createResponse(Response.OK, requestEvent.getRequest());
                                        okResponse = updateContactList(okResponse);
                                        state = "authReceived";
                                        dev.register();
                                        this.sendResponse(requestEvent, okResponse);
                                    } catch (ParseException ex) {
                                        logger.error(ex.getLocalizedMessage());
                                        //Logger.getLogger(SipListener.class.getName()).log(Level.SEVERE, null, ex);
                                    }
                                }
                            } else {
                                logger.error("nepodarilo sa overit meno a heslo");
                                Response notAuthResponse = sipServer.getSmFactory().createResponse(Response.UNAUTHORIZED, requestEvent.getRequest());
                                this.sendResponse(requestEvent, notAuthResponse);

                            }
                        } catch (NoSuchAlgorithmException ex) {
                            logger.error(ex.getLocalizedMessage());
                            //Logger.getLogger(SipListener.class.getName()).log(Level.SEVERE, null, ex);
                        } catch (ParseException ex) {
                            java.util.logging.Logger.getLogger(Registration.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    } catch (UnsupportedEncodingException ex) {
                        logger.error(ex.getLocalizedMessage());
                        //Logger.getLogger(SipListener.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    break;

                }
                case "authReceived": {
                    Response finalOkResponse = null;
                    try {
                        //              if (digestServerAuthHelper.doAuthenticateHashedPassword(requestEvent.getRequest(), createMd5(requestedUsername, requestedRealm, dev.getPasswd()))) {
                        finalOkResponse = sipServer.getSmFactory().createResponse(Response.OK, requestEvent.getRequest());
                        ContactHeader ch = (ContactHeader) requestEvent.getRequest().getHeader(ContactHeader.NAME);
                        ExpiresHeader eh = requestEvent.getRequest().getExpires();
                        if ((ch != null && ch.getExpires() == 0) || (eh != null && eh.getExpires() == 0)) {

                            state = "UNREGISTERED";
                            dev.unregister();
                            logger.info("UNREGISTERED ");
                        } else {
                            updateContactList(finalOkResponse);
                        }

//                        } else {
                        //                          Response notAuthResponse = sipServer.getSmFactory().createResponse(Response.UNAUTHORIZED, requestEvent.getRequest());
                        //                        this.sendResponse(requestEvent, notAuthResponse);
                        //                  }
                    } catch (ParseException ex) {
                        logger.error(ex.getLocalizedMessage());
                        //Logger.getLogger(SipListener.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    this.sendResponse(requestEvent, finalOkResponse);
                }
                break;

            }
        } catch (NullPointerException ex) {
            ex.printStackTrace();
        } catch (ParseException ex) {
            java.util.logging.Logger.getLogger(Registration.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    public Response updateContactList(Response okResponse) {
        try {
            SipURI sipU = sipServer.getSaFactory().createSipURI(dev.getExtension().toString(), sipServer.getSipDomain());
            Address addr = sipServer.getSaFactory().createAddress(this.getDev().getName(), sipU);
            ContactHeader contactHeader = sipServer.getShFactory().createContactHeader(addr);
// logger.info();
            okResponse.addHeader(contactHeader);
            SipURI sipUri = new SipUri();
            sipUri.setHost(this.sipServer.getSipDomain());
            sipUri.setUser(this.getDev().getName());
            ContactHeader extensionContactHeader = sipServer.getShFactory().createContactHeader(sipServer.getSaFactory().createAddress(this.getDev().getName(), sipUri));
            okResponse.addHeader(extensionContactHeader);

        } catch (ParseException ex) {
            java.util.logging.Logger.getLogger(Registration.class
                    .getName()).log(Level.SEVERE, null, ex);
        }
        return okResponse;

    }

    public void createCall(RequestEvent requestEvent) {
        try {
            CallSession session = null;
            if (!state.equals("authReceived")) {
                logger.error("neukoncena registracia");
            } else {
                logger.debug("looking for sessions");
                session = findSession(requestEvent);
                if (session != null) {
                    logger.debug("found session");

                    session.requestReceived(requestEvent);
                } else {
                    logger.debug("creating new session");
                    ToHeader toheader = (ToHeader) requestEvent.getRequest().getHeader(ToHeader.NAME);
                    logger.info("vytvorena to header");
                    FromHeader fromHeader = (FromHeader) requestEvent.getRequest().getHeader(FromHeader.NAME);
                    logger.info("vytvorena from header");
                    CallIdHeader callIdHeader = (CallIdHeader) requestEvent.getRequest().getHeader(CallIdHeader.NAME);

                    logger.info(fromHeader.getAddress().toString() + " -> " + toheader.getAddress().toString());

                    session = new CallSession(findRegistration(fromHeader.getAddress()), findRegistration(toheader.getAddress()), sipServer, callIdHeader);
                    sipServer.getCallSessionList().add(session);
                    session.requestReceived(requestEvent);
                }

            }

        } catch (NullPointerException ex) {
            ex.printStackTrace();
        }
    }

    public void sendResponse(Response resp, ServerTransaction st) {
        try {
            if (st != null) {
                try {
                    logger.info("SEND RESPONSE ->>>>>> OUT \n" + resp.toString());
                    st.sendResponse(resp);

                } catch (SipException | InvalidArgumentException ex) {
                    java.util.logging.Logger.getLogger(Registration.class
                            .getName()).log(Level.SEVERE, null, ex);
                }
            }
        } catch (NullPointerException np) {
            np.printStackTrace();
        }

    }

    public void sendResponse(RequestEvent requestEvent, Response resp) {
        st = sipServer.getST(requestEvent);
        try {

            st.sendResponse(resp);
            logger.info("SEND RESPONSE ->>>>>> OUT \n" + resp.toString());

        } catch (SipException | InvalidArgumentException ex) {
            logger.error(ex.getLocalizedMessage());
            //Logger.getLogger(SipListener.class
            //       .getName()).log(Level.SEVERE, null, ex);
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
        logger.error("DEVICE NOT FOUND");
        return null;
    }

    public CallSession findSession(RequestEvent requestEvent) {
        logger.debug("vyhladavam session ");
        for (CallSession cs : sipServer.getCallSessionList()) {
            logger.error("call id header finding " + cs.getCallIdHeader() + " -> " + requestEvent.getRequest().getHeader(CallIdHeader.NAME));
            if (cs.getCallIdHeader().equals((CallIdHeader) requestEvent.getRequest().getHeader(CallIdHeader.NAME))) {
                logger.error("nasiel som session ");

                return cs;
            }
        }
        return null;
    }

    public Registration findRegistration(Address Address) {
        try {
            logger.debug("vyhladavam registraciu");
            logger.debug("reglist size " + sipServer.getRegistrationList().size());
            for (Registration reg : sipServer.getRegistrationList()) {
                if (!reg.getState().equals("UNREGISTERED")) {
                    logger.debug("reg address -> " + ((SipURI) Address.getURI()).getUser() + " equals " + reg.getDev().getExtension() + " -> " + reg.getState());

                    //    if (reg.regHost != null && reg.regPort != null) {
                    SipURI s1 = (SipURI) Address.getURI();
                    logger.debug("mam sip uri " + s1.toString());
                    // equals(reg.getDev().getExtension().toString(); 
                    String regDevEx = reg.getDev().getExtension().toString();
                    logger.debug("mam ext " + regDevEx);
                    String regDevName = reg.getDev().getName();
                    logger.debug("mam name " + regDevName);
                    if (s1.getUser().equals(regDevEx) || s1.getUser().equals(regDevName)) {
                        logger.debug("found reg " + reg.getDev().getExtension());

                        return reg;
                        //      }
                    } else {
                        logger.debug("not UNREGISTERED but not matched");
                    }
                } else {
                    logger.debug("reg not matched");
                }
            }
        } catch (NullPointerException np) {
            np.printStackTrace();
        }
        logger.debug("vraciam null");
        return null;
    }

    public UserDevice getDev() {
        return dev;
    }

    public Integer getRegPort() {
        return regPort;
    }

    public String getRegHost() {
        return regHost;
    }

    public String getState() {
        return state;
    }

    public Server getSipServer() {
        return sipServer;
    }

}

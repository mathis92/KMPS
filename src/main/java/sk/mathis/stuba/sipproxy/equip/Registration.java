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
import java.util.ArrayList;
import java.util.Date;
import java.util.logging.Level;
import javax.sip.InvalidArgumentException;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
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
    private ServerTransaction st;

    private static final Logger logger = LoggerFactory.getLogger(Registration.class);

    public Registration(Server sipServer) {
        this.contactHeaderList = new ArrayList();
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
            logger.info("IN ->>>> " + requestEvent.getRequest().toString());
            ViaHeader vheader = (ViaHeader) requestEvent.getRequest().getHeader("via");
            // FromHeader fromHeader = (FromHeader) requestEvent.getRequest().getHeader(FromHeader.NAME);
            switch (state) {
                case "regReceived": {
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
                                        okResponse = updateContactList(okResponse);
                                        state = "authReceived";
                                        this.sendResponse(requestEvent, okResponse);
                                    } catch (ParseException ex) {
                                        logger.error(ex.getLocalizedMessage());
                                        //Logger.getLogger(SipListener.class.getName()).log(Level.SEVERE, null, ex);
                                    }
                                }
                            } else {
                                logger.error("nepodarilo sa overit meno a heslo");
                            }
                        } catch (NoSuchAlgorithmException ex) {
                            logger.error(ex.getLocalizedMessage());
                            //Logger.getLogger(SipListener.class.getName()).log(Level.SEVERE, null, ex);
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
                        finalOkResponse = sipServer.getSmFactory().createResponse(Response.OK, requestEvent.getRequest());
                        updateContactList(finalOkResponse);
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
        }

    }

    public Response updateContactList(Response okResponse) {
        try {
            ContactHeader contactHeader = sipServer.getShFactory().createContactHeader(sipServer.getSaFactory().createAddress(this.getDev().getName(), sipServer.getSaFactory().createSipURI(dev.getName(), sipServer.getSipDomain())));
            // logger.info();
            okResponse.addHeader(contactHeader);
            SipURI sipUri = new SipUri();
            sipUri.setHost(this.sipServer.getSipDomain());
            sipUri.setUser(this.getDev().getExtension().toString());
            ContactHeader extensionContactHeader = sipServer.getShFactory().createContactHeader(sipServer.getSaFactory().createAddress(this.getDev().getExtension().toString(), sipUri));
            okResponse.addHeader(extensionContactHeader);
        } catch (ParseException ex) {
            java.util.logging.Logger.getLogger(Registration.class.getName()).log(Level.SEVERE, null, ex);
        }
        return okResponse;

    }

    public void createCall(RequestEvent requestEvent) {
        try {
            CallSession session = null;
            if (!state.equals("authReceived")) {
                logger.error("neukoncena registracia");
            } else {
                if (sipServer.getCallSessionList().isEmpty()) {
                    ToHeader toheader = (ToHeader) requestEvent.getRequest().getHeader(ToHeader.NAME);
                    FromHeader fromHeader = (FromHeader) requestEvent.getRequest().getHeader(FromHeader.NAME);
                    CallIdHeader callIdHeader = (CallIdHeader) requestEvent.getRequest().getHeader(CallIdHeader.NAME);

                    logger.info(fromHeader.getAddress().toString() + " -> " + toheader.getAddress().toString());

                    session = new CallSession(findRegistration(fromHeader.getAddress()), findRegistration(toheader.getAddress()), sipServer, callIdHeader);
                    sipServer.getCallSessionList().add(session);
                    session.requestReceived(requestEvent);
                } else {
                    logger.info("looking for sessions");
                    session = findSession(requestEvent);
                    if (session != null) {
                        logger.info("found session");

                        session.requestReceived(requestEvent);
                    }
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
                    logger.info("sending response on serverTransaction " + resp.toString());
                    st.sendResponse(resp);
                } catch (SipException | InvalidArgumentException ex) {
                    java.util.logging.Logger.getLogger(Registration.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        } catch (NullPointerException np) {
            np.printStackTrace();
        }

    }

    public void sendResponse(RequestEvent requestEvent, Response resp) {
        st = sipServer.getST(requestEvent);
        try {
            if (st == null) {
                st = this.sipServer.getsProvider().getNewServerTransaction(requestEvent.getRequest());
            }
            st.sendResponse(resp);
            logger.info("OUT : ->>>>>>>>>>>>>> " + resp.toString());

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

    public Registration findRegistration(Address Address) {
        logger.info("----------------- PHUCK");
        for (Registration reg : sipServer.getRegistrationList()) {

            logger.info("reg address -> " + ((SipURI) Address.getURI()).getUser() + " equals " + reg.getDev().getExtension());
            if (((SipURI) Address.getURI()).getUser().equals(reg.getDev().getExtension().toString()) || ((SipURI) Address.getURI()).getUser().equals(reg.getDev().getName())) {
                logger.info("found reg " + reg.getDev().getExtension());
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

    public String getRegHost() {
        return regHost;
    }

}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package sk.mathis.stuba.sipproxy.equip;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Date;
import java.util.Properties;
import java.util.TooManyListenersException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sip.InvalidArgumentException;
import javax.sip.ListeningPoint;
import javax.sip.ObjectInUseException;
import javax.sip.PeerUnavailableException;
import javax.sip.RequestEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipFactory;
import javax.sip.SipProvider;
import javax.sip.SipStack;
import javax.sip.TransactionAlreadyExistsException;
import javax.sip.TransactionUnavailableException;
import javax.sip.TransportNotSupportedException;
import javax.sip.address.AddressFactory;
import javax.sip.header.HeaderFactory;
import javax.sip.message.MessageFactory;
import org.apache.log4j.BasicConfigurator;
import org.slf4j.LoggerFactory;

/**
 *
 * @author martinhudec
 */
public class Server {

    private SipFactory sipFactory;
    private SipStack sipStack;
    private MessageFactory smFactory;
    private HeaderFactory shFactory;
    private AddressFactory saFactory;
    private String sipDomain;
    private ArrayList<CallSession> callSessionList;
    private SipListener sipLiastener;
    private SipProvider sipProvider;
    private ArrayList<Registration> registrationList;
    private Users users;
    private Integer sipPort;
    private String sipTransport;
    private final org.slf4j.Logger logger;

    ServerTransaction st;

    public Server() {
        this.logger = LoggerFactory.getLogger(Server.class);
    }

    public void initialize() throws TransportNotSupportedException, InvalidArgumentException, ObjectInUseException, TooManyListenersException {

        try {
            this.users = new Users();
            this.registrationList = new ArrayList();
            this.sipDomain = "10.8.48.87";
            this.sipPort = 5060;
            this.sipTransport = "UDP";
            this.sipFactory = SipFactory.getInstance();
            this.sipFactory.setPathName("gov.nist");
            Properties sipStackProperties = new Properties();
            sipStackProperties.setProperty("javax.sip.STACK_NAME", this.sipDomain);
            this.sipStack = this.sipFactory.createSipStack(sipStackProperties);
            this.smFactory = this.sipFactory.createMessageFactory();
            this.shFactory = this.sipFactory.createHeaderFactory();
            this.saFactory = this.sipFactory.createAddressFactory();
            this.callSessionList = new ArrayList();
            this.sipLiastener = new SipListener(this);
            ListeningPoint lp = this.sipStack.createListeningPoint("10.8.48.87", 5060, "UDP");
            this.sipProvider = this.sipStack.createSipProvider(lp);
            this.getSipProvider().addSipListener(sipLiastener);
        } catch (PeerUnavailableException ex) {
            Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
        } catch (NullPointerException np) {
            np.printStackTrace();
        }
    }

    public AddressFactory getSaFactory() {
        return saFactory;
    }

    public HeaderFactory getShFactory() {
        return shFactory;
    }

    public String getSipDomain() {
        return sipDomain;
    }

    public SipFactory getSipFactory() {
        return sipFactory;
    }

    public SipStack getSipStack() {
        return sipStack;
    }

    public MessageFactory getSmFactory() {
        return smFactory;
    }

    public SipProvider getsProvider() {
        return getSipProvider();
    }

    /**
     * @return the sipProvider
     */
    public SipProvider getSipProvider() {
        return sipProvider;
    }

    public SipListener getSipLiastener() {
        return sipLiastener;
    }

    public Users getUsers() {
        return users;
    }

    public ServerTransaction getST(RequestEvent requestEvent) {
        if (requestEvent.getServerTransaction() != null) {
            return requestEvent.getServerTransaction();
        } else {
            try {
                return this.sipProvider.getNewServerTransaction(requestEvent.getRequest());
            } catch (TransactionAlreadyExistsException ex) {
                Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
            } catch (TransactionUnavailableException ex) {
                Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return null;
    }

    public String createBranch() {
        String branch = "z9hG4bK" + Long.toString(new Date().getTime());
        return branch;
    }

    public ArrayList<Registration> getRegistrationList() {
        return registrationList;
    }

    public ServerTransaction getSt() {
        return st;
    }

    public String getSipTransport() {
        return sipTransport;
    }

    public ArrayList<CallSession> getCallSessionList() {
        return callSessionList;
    }

    public Integer getSipPort() {
        return sipPort;
    }

}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package sk.mathis.stuba.sipproxy.equip;

import java.util.ArrayList;
import java.util.Properties;
import java.util.TooManyListenersException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sip.InvalidArgumentException;
import javax.sip.ListeningPoint;
import javax.sip.ObjectInUseException;
import javax.sip.PeerUnavailableException;
import javax.sip.SipFactory;
import javax.sip.SipProvider;
import javax.sip.SipStack;
import javax.sip.TransportNotSupportedException;
import javax.sip.address.AddressFactory;
import javax.sip.header.HeaderFactory;
import javax.sip.message.MessageFactory;

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
    private SipListener sipLiastener;
    private SipProvider sipProvider;
    private ArrayList<Registration> registrationList;
    private Users users;


    public Server() {
    }

    public void initialize() throws TransportNotSupportedException, InvalidArgumentException, ObjectInUseException, TooManyListenersException {
 
        try {
            this.users = new Users();
            this.registrationList = new ArrayList();
            this.sipDomain = "192.168.1.103";
            this.sipFactory = SipFactory.getInstance();
            this.sipFactory.setPathName("gov.nist");
            Properties sipStackProperties = new Properties();
            sipStackProperties.setProperty("javax.sip.STACK_NAME", this.sipDomain);
            this.sipStack = this.sipFactory.createSipStack(sipStackProperties);
            this.smFactory = this.sipFactory.createMessageFactory();
            this.shFactory = this.sipFactory.createHeaderFactory();
            this.saFactory = this.sipFactory.createAddressFactory();
            this.sipLiastener = new SipListener(this);
            ListeningPoint lp = this.sipStack.createListeningPoint("192.168.1.103",5060,"UDP");
            this.sipProvider = this.sipStack.createSipProvider(lp);
            this.getSipProvider().addSipListener(sipLiastener);
            
        } catch (PeerUnavailableException ex) {
            Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
        } catch (NullPointerException np ){
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

    public ArrayList<Registration> getRegistrationList() {
        return registrationList;
    }

}

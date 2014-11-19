/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package sk.mathis.stuba.sipproxy.equip;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.TooManyListenersException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonReaderFactory;
import javax.json.JsonWriter;
import javax.json.stream.JsonGenerator;
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
import org.slf4j.LoggerFactory;
import sk.mathis.stuba.sipproxy.AppGui;

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
    private final AppGui gui;
    private StringBuilder sb;
    PrintStream tmpStreamOut = System.out;
    PrintStream tmpStreamErr = System.err;

    ServerTransaction st;

    public Server(AppGui gui) {
        this.gui = gui;
        this.logger = LoggerFactory.getLogger(Server.class);
    }

    public void initialize() throws TransportNotSupportedException, InvalidArgumentException, ObjectInUseException, TooManyListenersException {

        try {
            this.users = new Users(readUsers());
            this.registrationList = new ArrayList();

            this.sipDomain = readConfig().get(0);
            this.sipPort = Integer.parseInt(readConfig().get(1));
            this.sipTransport = readConfig().get(2);

            //wirteSipConfig(sipTransport, sipDomain, sipPort);
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
            ListeningPoint lp = this.sipStack.createListeningPoint(sipDomain, sipPort, sipTransport);
            this.sipProvider = this.sipStack.createSipProvider(lp);
            this.getSipProvider().addSipListener(sipLiastener);
        } catch (PeerUnavailableException ex) {
            Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
        } catch (NullPointerException np) {
            np.printStackTrace();
        } catch (FileNotFoundException ex) {
            Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
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

    public void wirteSipConfig(String transport, String domain, Integer port) {
        FileOutputStream fos = null;
        try {
            JsonObjectBuilder configObject = Json.createObjectBuilder();

            fos = new FileOutputStream("/Users/martinhudec/Desktop/sipConfig.rtf");
            JsonArrayBuilder arrayObject = Json.createArrayBuilder();
            configObject.add("domain", domain);
            configObject.add("port", port);
            configObject.add("transport", transport);
            JsonWriter jw = Json.createWriter(fos);
            jw.writeObject(configObject.build());
        } catch (FileNotFoundException ex) {
            Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                fos.close();
            } catch (IOException ex) {
                Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
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

    public AppGui getGui() {
        return gui;
    }

    public void writeLogStdOut() {
        System.setOut(tmpStreamOut);
        System.setErr(tmpStreamErr);
    }

    public JsonArray readUsers() throws FileNotFoundException {
        JsonReaderFactory jrf = Json.createReaderFactory(null);
        FileInputStream fis = new FileInputStream(new File("/Users/martinhudec/Desktop/users.txt"));
        try(JsonReader jr = jrf.createReader(fis)){
            JsonObject jo = jr.readObject();
            JsonArray ja = jo.getJsonArray("users");
            jr.close();
            return ja;
        }
        
        
    }
    
    public ArrayList<String> readConfig() throws FileNotFoundException {
        Map<String, Boolean> configMap = new HashMap<>();
        configMap.put(JsonGenerator.PRETTY_PRINTING, Boolean.TRUE);
        JsonReaderFactory jrf = Json.createReaderFactory(configMap);

        FileInputStream fis = new FileInputStream(new File("/Users/martinhudec/Desktop/sipConfig.txt"));
        
        try (JsonReader jr = jrf.createReader(fis)){
            
            JsonObject jo = jr.readObject();
            JsonObject object = jo.getJsonObject("sipConfig");

            ArrayList<String> config = new ArrayList<>();
            config.add(object.getString("sipServerDomain"));
            config.add(object.getString("sipServerPort"));
            config.add(object.getString("sipTransport"));
            jr.close();
            return config;
        }
    }

    public void writeLog() {

        PrintStream printStream = new PrintStream(new TextAreaOutputStream(gui.getLogTextArea()));
        System.setOut(printStream);
        System.setErr(printStream);

    }

}

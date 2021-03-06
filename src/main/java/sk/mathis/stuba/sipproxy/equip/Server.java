/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package sk.mathis.stuba.sipproxy.equip;

import java.awt.Window;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
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
import javax.json.JsonValue;
import javax.json.JsonWriter;
import javax.json.JsonWriterFactory;
import javax.json.stream.JsonGenerator;
import javax.json.stream.JsonGeneratorFactory;
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
import jdk.nashorn.internal.ir.debug.JSONWriter;
import org.slf4j.LoggerFactory;
import sk.mathis.stuba.sipproxy.AppGui;
import com.google.common.base.Preconditions;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

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
            this.enableOSXFullscreen(gui);
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

    
    public static void enableOSXFullscreen(Window window) {
        try {
            Preconditions.checkNotNull(window);
            
            Class util = Class.forName("com.apple.eawt.FullScreenUtilities");
            Class params[] = new Class[]{Window.class, Boolean.TYPE};
            Method method = util.getMethod("setWindowCanFullScreen", params);
            method.invoke(util, window, true);
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
        } catch (NoSuchMethodException ex) {
            Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
        } catch (SecurityException ex) {
            Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalArgumentException ex) {
            Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvocationTargetException ex) {
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
                Logger.getLogger(Server.class
                        .getName()).log(Level.SEVERE, null, ex);
            } catch (TransactionUnavailableException ex) {
                Logger.getLogger(Server.class
                        .getName()).log(Level.SEVERE, null, ex);
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
        try (JsonReader jr = jrf.createReader(fis, Charset.defaultCharset())) {
            JsonObject jo = jr.readObject();
            JsonArray ja = jo.getJsonArray("users");
            return ja;
        }
    }

    public void removeUser(String extension){
        for(UserDevice dev : users.getUsersList()){
            if(dev.getExtension().toString().equals(extension)){
                users.getUsersList().remove(dev);
            }
        }
    }
    
    public void addUser(String name, String extension, String password){
        users.getUsersList().add(new UserDevice(name, password, Integer.parseInt(extension)));
    }
    
    public ArrayList<String> readConfig() throws FileNotFoundException {
        Map<String, Boolean> configMap = new HashMap<>();
        configMap.put(JsonGenerator.PRETTY_PRINTING, Boolean.TRUE);
        JsonReaderFactory jrf = Json.createReaderFactory(configMap);

        FileInputStream fis = new FileInputStream(new File("/Users/martinhudec/Desktop/sipConfig.txt"));

        try (JsonReader jr = jrf.createReader(fis)) {

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

}

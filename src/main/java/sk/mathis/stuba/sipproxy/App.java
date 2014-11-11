/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package sk.mathis.stuba.sipproxy;

import java.util.Date;
import java.util.TooManyListenersException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.sip.SipFactory;
import javax.sip.InvalidArgumentException;
import javax.sip.ObjectInUseException;
import javax.sip.TransportNotSupportedException;
import org.mobicents.servlet.sip.message.SipFactoryImpl;
import sk.mathis.stuba.sipproxy.equip.Server;
import sk.mathis.stuba.sipproxy.equip.SipListener;

/**
 *
 * @author martinhudec
 */
public class App {

   private SipFactory sipFactory;
   private static SipListener sipListener; 
   private static Server srvr = new Server();
   public static void main(String[] args) {
    
       try {
           srvr.initialize();
       } catch (TransportNotSupportedException ex) {
           Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, ex);
       } catch (InvalidArgumentException ex) {
           Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, ex);
       } catch (ObjectInUseException ex) {
           Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, ex);
       } catch (TooManyListenersException ex) {
           Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, ex);
       }
    
   // sipListener = new SipListener(srvr);
    
    }

}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package sk.mathis.stuba.sipproxy.equip;

/**
 *
 * @author martinhudec
 */
import java.io.IOException;
import java.io.OutputStream;
import javax.swing.JTextArea;

public class TextAreaOutputStream extends OutputStream {

    private JTextArea textArea = null;
    public TextAreaOutputStream(JTextArea textArea) {
        this.textArea = textArea;
    }
        

    @Override
    public void write(int b) throws IOException {
        
        textArea.append(String.valueOf((char)b));
        // scrolls the text area to the end of data
        textArea.setCaretPosition(textArea.getDocument().getLength());
    
    
    }
    


}

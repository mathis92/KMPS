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
public class UserDevice {

    private String name = "telefon";
    private String passwd = "heslo";
    private Integer port = null;
    private String host = null;

    public UserDevice(String name, String passwd) {
        this.name = name;
        this.passwd = passwd;

    }

    public String getHost() {
        return host;
    }

    public String getName() {
        return name;
    }

    public String getPasswd() {
        return passwd;
    }

    public Integer getPort() {
        return port;
    }


    public void setHost(String host) {
        this.host = host;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPasswd(String passwd) {
        this.passwd = passwd;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package sk.mathis.stuba.sipproxy.equip;

import java.util.ArrayList;

/**
 *
 * @author martinhudec
 */
public class Users {

    ArrayList<UserDevice> usersList = new ArrayList<>();

    public Users() {
        usersList.add(new UserDevice("phone", "heslo",101));
        usersList.add(new UserDevice("ntb", "heslo",100));
    }

    public ArrayList<UserDevice> getUsersList() {
        return usersList;
    }

}

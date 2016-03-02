package net.bestia.gmserver.servlets;

import javax.servlet.annotation.WebServlet;

import com.vaadin.annotations.VaadinServletConfiguration;
import com.vaadin.server.VaadinServlet;

import net.bestia.gmserver.ui.LoginUI;

@SuppressWarnings("serial")
@WebServlet( asyncSupported = true)
@VaadinServletConfiguration(productionMode = false, ui = LoginUI.class)
public class LoginServlet extends VaadinServlet  {

}

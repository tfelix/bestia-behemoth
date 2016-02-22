package net.bestia.gmserver;

import javax.servlet.annotation.WebServlet;

import com.vaadin.annotations.VaadinServletConfiguration;
import com.vaadin.server.VaadinServlet;

import net.bestia.gmserver.vaadin.VaadinHelloWorld;

@WebServlet(value = "/*", asyncSupported = true)
@VaadinServletConfiguration(productionMode = false, ui = VaadinHelloWorld.class)
public class MyVaadinServlet extends VaadinServlet {

}

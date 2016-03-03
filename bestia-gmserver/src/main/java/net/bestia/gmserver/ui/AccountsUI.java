package net.bestia.gmserver.ui;


import org.vaadin.spring.VaadinUI;

import com.vaadin.annotations.Theme;
import com.vaadin.server.VaadinRequest;
import com.vaadin.ui.Label;
import com.vaadin.ui.UI;

@SuppressWarnings("serial")
@Theme("valo")
@VaadinUI
public class AccountsUI extends UI {

	@Override
	protected void init(VaadinRequest request) {
		
		setContent(new Label("Hello! I'm the root UI!"));
		
	}

}

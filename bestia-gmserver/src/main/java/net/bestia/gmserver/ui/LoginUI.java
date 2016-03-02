package net.bestia.gmserver.ui;

import com.vaadin.server.VaadinRequest;
import com.vaadin.ui.Button;
import com.vaadin.ui.Label;
import com.vaadin.ui.Notification;
import com.vaadin.ui.UI;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;

@SuppressWarnings("serial")
public class LoginUI extends UI {

	@Override
	protected void init(VaadinRequest request) {
		// Create the content root layout for the UI
		VerticalLayout content = new VerticalLayout();
		setContent(content);

		// Display the greeting
		content.addComponent(new Label("Login"));

		// Have a clickable button
		ClickListener listener = new ClickListener() {

			@Override
			public void buttonClick(ClickEvent event) {
				Notification.show("Pushed!");
			}
		};

		content.addComponent(new Button("Einloggen", listener));
	}

}
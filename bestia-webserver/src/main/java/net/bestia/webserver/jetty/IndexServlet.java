package net.bestia.webserver.jetty;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


public class IndexServlet extends HttpServlet {   
	 
	private static final long serialVersionUID = 1L;

	@Override
	  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
	  {
	    PrintWriter out = resp.getWriter();
	    out.println("hello, world");
	    out.close();
	  }
	}
